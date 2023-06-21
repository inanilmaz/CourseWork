package searchengine.services;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.config.Site;
import searchengine.model.PageTable;
import searchengine.model.SiteTable;
import searchengine.model.StatusEnum;
import searchengine.repositories.PageRepositories;
import searchengine.repositories.SiteRepositories;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.RecursiveTask;

@Service
public class IndexingService extends RecursiveTask<Set<String>> {

    private final Site site;
    private final SiteTable siteTable;
    private Connection.Response response = null;
    private SiteTable updateSiteTable;
    @Autowired
    private  SiteRepositories siteRepositories;
    @Autowired
    private  PageRepositories pageRepositories;
    private HashSet<String> page = new HashSet<>();

    public IndexingService(Site site) {
        this.site = site;
        siteTable = new SiteTable();
    }
    public void createNewSite(){
        siteTable.setName(site.getName());
        siteTable.setUrl(site.getUrl());
        siteTable.setStatusTime(LocalDateTime.now());
        siteTable.setStatus(StatusEnum.INDEXING);
        siteRepositories.save(siteTable);
    }

    public void deleteAllEntries(){
        List<SiteTable> allSiteTables = siteRepositories.findAll();
        List<PageTable> allPageTables = pageRepositories.findAll();
        for(SiteTable siteTable:allSiteTables){
            for(PageTable pageTable : allPageTables) {
                pageRepositories.deleteById(pageTable.getId());
            }
               siteRepositories.deleteById(siteTable.getId());
        }
    }
    public boolean checkPage(String href,String url){
        if(href.contains(changeUrl(url))
                && !href.contains("#")
                && !href.contains("pdf")
                && !href.equals(url)){
            return true;
        }
        return false;
    }
    public String changeUrl(String url){
        int start = url.indexOf(".");
        return url.substring(start).replaceFirst(".","");
    }

    public void crawlPage(int statusCode) throws IOException {

        Document doc = response.parse();
        Elements links = doc.select("a[href]");
        for(Element link : links){
            String href = link.attr("abs:href");
            if(checkPage(href,site.getUrl())){
                PageTable pageTable = new PageTable();
                pageTable.setSiteId(siteTable);
                pageTable.setPath(href.replaceAll(site.getUrl(),""));
                pageTable.setContent(doc.getAllElements().toString());
                pageTable.setCode(statusCode);
                pageRepositories.save(pageTable);
                updateDateTime();
            }
        }
        updateStatusToIndexed();

    }

    private void updateStatusToFailed() {
        List<SiteTable> allSiteTables = siteRepositories.findAll();
        for(SiteTable st : allSiteTables){
            if(siteTable.getUrl().equals(st.getUrl())){
                st.setStatus(StatusEnum.FAILED);
                siteRepositories.save(st);
            }
        }
    }

    private void updateDateTime() {
        List<SiteTable> allSiteTables = siteRepositories.findAll();
        for(SiteTable st : allSiteTables){
            if(siteTable.getUrl().equals(st.getUrl())){
                updateSiteTable = st;
                updateSiteTable.setStatusTime(LocalDateTime.now());
                siteRepositories.save(updateSiteTable);
            }
        }
    }
    private void updateStatusToIndexed(){
        updateSiteTable.setStatus(StatusEnum.INDEXED);
        siteRepositories.save(updateSiteTable);
    }

    public void parsePage() throws IOException {
        response = Jsoup.connect(site.getUrl()).userAgent("Mozilla/5.0 (Windows; U; WindowsNT 5.1; en-US; rv1.8.1.6) Gecko/20070725 Firefox/2.0.0.6")
                .referrer("http://www.google.com")
                .execute();
        int statusCode = response.statusCode();
        crawlPage(statusCode);


    }

    @Override
    protected Set<String> compute() {
        deleteAllEntries();
        createNewSite();
        try {
            parsePage();
            return page;
        } catch (IOException e) {
            updateStatusToFailed();
            return new HashSet<>();
        }
    }
    
}
