package dk.statsbiblioteket.summa.support.embeddedsolr;

import static org.junit.Assert.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import dk.statsbiblioteket.summa.common.configuration.Resolver;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.impl.HttpSolrServer;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.params.SolrParams;
import org.junit.Before;
import org.junit.Test;

public class SolrSearchConfigTest {
    
    String solrHome = "support/solr_home1"; //data-dir (index) will be created here.
    String context = "/solr";
    int port = 8983;

    @Before
    public void setUp() throws Exception {
        System.setProperty("basedir", "."); //for logback
    }

    @Test
    public void testEmbeddedJettyWithSolr() throws Exception {

        //Start up webserver
        EmbeddedJettyWithSolrServer server =  new EmbeddedJettyWithSolrServer(
            Resolver.getFile(solrHome).toString(), context, port);
        server.run();
        
        //Wrap the server with HTTPSolrServer      
        String url = server.getServerUrl();
        SolrServer server1 = new HttpSolrServer(url);
        //Thread.sleep(5000000L); //use to test server in browser. Or ingest data etc.

        //This is not found. There is no data in index
        SolrQuery query = new SolrQuery("atsun");
        query.setFacet(true);
        query.setParam("spellcheck", "true");
        query.setParam("spellcheck.dictionary", "summa_spell");
        
      
        QueryResponse response = server1.query(query);
        System.out.println("found:"+response.getResults().getNumFound());
        System.out.println("found:"+response.getSpellCheckResponse().getSuggestions().size());
        //   assertEquals(0L, response.getResults().getNumFound());

        
        //  Direct REST call. /edismax is defined to search in all summa-fields with boosts
        //String httpResponse = SolrServerUnitTestUtil.callURL(url+"/edismax/?q=datsun&spellcheck=true");
        String httpResponse = SolrServerUnitTestUtil.callURL(url+"/select/?spellcheck=true&spellcheck.dictionary=summa_spell&spellcheck.extendedResults=true&spellcheck.maxCollationsTries=5&spellcheck.collate=true&spellcheck.collateExtendedResults=true&spellcheck.count=5&q=atsun");
        System.out.print(httpResponse);//No data
    }

 
}