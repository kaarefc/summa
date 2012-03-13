package dk.statsbiblioteket.summa.support.harmonise;

import dk.statsbiblioteket.summa.common.configuration.Configuration;
import dk.statsbiblioteket.summa.search.api.Request;
import dk.statsbiblioteket.summa.search.api.Response;
import dk.statsbiblioteket.summa.search.api.ResponseCollection;
import dk.statsbiblioteket.summa.search.api.document.DocumentKeys;
import dk.statsbiblioteket.summa.search.api.document.DocumentResponse;
import dk.statsbiblioteket.summa.support.summon.search.SummonSearchNode;
import dk.statsbiblioteket.util.Strings;
import dk.statsbiblioteket.util.qa.QAInfo;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.List;

/**
 * Tests that the rewriter produces semantically equivalent queries when the term queries are not rewritten
 */
@QAInfo(level = QAInfo.Level.NORMAL,
        state = QAInfo.State.IN_DEVELOPMENT,
        author = "hsm")
public class QueryRewriterIntegrationTest extends TestCase {
    private static Log log = LogFactory.getLog(
            QueryRewriterIntegrationTest.class);

    private static final File SECRET = new File(
            System.getProperty("user.home") + "/summon-credentials.dat");
    private String id;
    private String key;

    private SummonSearchNode summon;
    private QueryRewriter requestRewriter;

    public static Test suite() {
        return new TestSuite(QueryRewriterIntegrationTest.class);
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();
        if (!SECRET.exists()) {
            throw new IllegalStateException(
                    "The file '" + SECRET.getAbsolutePath() + "' must exist and "
                            + "contain two lines, the first being access ID, the second"
                            + "being access key for the Summon API");
        }
        BufferedReader br = new BufferedReader(new InputStreamReader(
                new FileInputStream(SECRET), "utf-8"));
        id = br.readLine();
        key = br.readLine();
        br.close();
        log.debug("Loaded credentials from " + SECRET);

        Configuration conf = Configuration.newMemoryBased(
                SummonSearchNode.CONF_SUMMON_ACCESSID, id,
                SummonSearchNode.CONF_SUMMON_ACCESSKEY, key
        );

        summon = new SummonSearchNode(conf);
        requestRewriter = new QueryRewriter(new QueryRewriter.Event() {
            @Override
            public Query onQuery(TermQuery query) {
                return query;
            }
        });
    }

    @Override
    public void tearDown() {
        summon = null;
        requestRewriter = null;
    }

    public void testRewrite1() throws RemoteException, ParseException {
        checkQuery("foo OR (bar AND baz)");
    }

    private void checkQuery(String query)
            throws RemoteException, ParseException {
        String rewritten = requestRewriter.rewrite(query);
        compareHits(query, search(query), search(rewritten));
    }

    // Avoids query rewriting inside SummonSearchNode
    private void checkQueryDirect(String query)
            throws RemoteException, ParseException {
        String rewritten = requestRewriter.rewrite(query);
        compareHits(query, searchDirect(query), searchDirect(rewritten));
    }

    private void compareHits(String query, ResponseCollection rc1, ResponseCollection rc2) {
        String rs1 = Strings.join(getResults(rc1), ", ");
        assertEquals("Expected equality for query '" + query + "'",
                     rs1, Strings.join(getResults(rc2), ", "));
        assertFalse("There was no result for '" + query + "'", "".equals(rs1));
    }

    private void compareHits(String query, boolean shouldMatch, ResponseCollection rc1, ResponseCollection rc2) {
        if (shouldMatch) {
            assertEquals("Expected equality for query '" + query + "'",
                         Strings.join(getResults(rc1), ", "), Strings.join(getResults(rc2), ", "));
        } else {
            String qr1 = Strings.join(getResults(rc1), ", ");
            String qr2 = Strings.join(getResults(rc2), ", ");
            if (qr1.equals(qr2)) {
                fail("Expected non-equality for query '" + query + "' with result '" + qr1 + "'");
            }
        }
    }

    private List<String> getResults(ResponseCollection responses) {
        for (Response response : responses) {
            if (response instanceof DocumentResponse) {
                DocumentResponse dr = (DocumentResponse)response;
                ArrayList<String> results = new ArrayList<String>((int)dr.getHitCount());
                for (DocumentResponse.Record record: dr.getRecords()) {
                    results.add(record.getId());
                }
                return results;
            }
        }
        return null;
    }

    private int countResults(ResponseCollection responses) {
        for (Response response : responses) {
            if (response instanceof DocumentResponse) {
                return (int) ((DocumentResponse) response).getHitCount();
            }
        }
        throw new IllegalArgumentException(
                "No documentResponse in ResponseCollection");
    }

    private ResponseCollection search(String query) throws RemoteException {
        ResponseCollection responses = new ResponseCollection();
        Request request = new Request();
        request.put(DocumentKeys.SEARCH_QUERY, query);
        request.put(DocumentKeys.SEARCH_COLLECT_DOCIDS, true);

        summon.search(request, responses);

        return responses;
    }

    private ResponseCollection searchDirect(String query) throws RemoteException {
        ResponseCollection responses = new ResponseCollection();
        Request request = new Request();
        request.put(DocumentKeys.SEARCH_QUERY, query);
        request.put(DocumentKeys.SEARCH_COLLECT_DOCIDS, true);
        request.put(SummonSearchNode.SEARCH_PASSTHROUGH_QUERY, true);

        summon.search(request, responses);

        return responses;
    }

    public void testRewrite2() throws RemoteException, ParseException {
        checkQuery("foo AND (bar AND baz)");
    }

    public void testRewrite3() throws RemoteException, ParseException {
        checkQuery("foo AND bar AND baz");
    }

    public void testRewrite4() throws RemoteException, ParseException {
        //  Summon no longer adheres to Lucene's way of parsing boolean queries.
        //
        // Details: http://wiki.apache.org/lucene-java/BooleanQuerySyntax
        // https://issues.apache.org/jira/browse/LUCENE-1823
        // https://issues.apache.org/jira/browse/LUCENE-167
        //
        // That means query does not return the same number of hits
        String query = "foo AND bar AND baz OR spam AND eggs OR ham";
        assertFalse(countResults(search(query)) == countResults(search(requestRewriter.rewrite(query))));
    }

    public void testRewrite4Alternative() throws RemoteException, ParseException {
        // This is what Summon actually does (for the moment). AND has higher preference than OR and both are
        // left associative. This is the common way to handle logical operators.
        String query = "foo AND bar AND baz OR spam AND eggs OR ham";
        requestRewriter.rewrite(query);
        String andBindsHarder = "((foo AND bar) AND baz) OR (spam AND eggs) OR ham";

        compareHits(query, search(query), search(andBindsHarder));
        compareHits(query, search(query), search(requestRewriter.rewrite(andBindsHarder)));
    }

    public void testRewrite5() throws RemoteException, ParseException {
        String query = "foo AND +bar AND baz OR +(-spam) AND (eggs OR -ham)";
        assertFalse(countResults(search(query)) == countResults(search(requestRewriter.rewrite(query))));
    }

    public void testRewrite6() throws RemoteException, ParseException {
        checkQuery("foo AND bar AND baz OR spam");
    }

    public void testRewrite7() throws RemoteException, ParseException {
        String query = "foo OR bar AND baz";
        assertFalse(countResults(search(query)) == countResults(search(requestRewriter.rewrite(query))));
    }

    public void testRewrite7Alternative() throws RemoteException, ParseException {
        String query = "foo OR bar AND baz";
        String rewritten = "(foo OR (+bar +baz))";

        compareHits(query + "' and '" + rewritten, search(query), search(rewritten));
    }

    public void testRewrite8() throws RemoteException, ParseException {
        checkQuery("foo AND bar OR baz");
    }

    public void testRewriteBZ2416() throws RemoteException, ParseException {
        checkQuery("Ungdomsuddannelse - Sociale Publikationer");
    }

    public void testRewriteWithField() throws RemoteException, ParseException {
        checkQuery("chaos -recordBase:sb*");
    }

    public void testRewriteDivider() throws RemoteException, ParseException {
        checkQuery("foo - bar");
    }

    public void testRewriteTitleMatch() throws RemoteException, ParseException {
        checkQuery("miller genre as social action");
    }

    public void testRewriteTitleMatchDirect() throws RemoteException, ParseException {
        checkQueryDirect("miller genre as social action");
    }

    public void testRewriteTitleMatchWeightDirect() throws RemoteException, ParseException {
        checkQueryDirect("miller genre^2 as social action");
    }

    public void testColonSpace() throws RemoteException, ParseException {
        checkQuery("foo: bar");
    }

    // This fails as the DisMax treats the direct query as "+foo +bar +baz", which it does not support.
    // The rewritten query is reduced to "foo bar baz".
//    public void testRewriteExplicitAndDirect() throws RemoteException, ParseException {
//        checkQueryDirect("foo AND bar AND baz");
//    }

    public void testApostrophes() throws RemoteException, ParseException {
        String simple = "miller genre as social action";
        String apostrophed = "\"miller\" \"genre\" \"as\" \"social\" \"action\"";
        compareHits(simple, searchDirect(simple), searchDirect(apostrophed));
    }

    public void testSpecificTitle() throws RemoteException, ParseException {
        checkQuery("towards a cooperative experimental system development approach");
        checkQueryDirect("towards a cooperative experimental system development approach");
    }

    public void testApostrophesExplicitAnd() throws RemoteException, ParseException {
        String simple = "miller genre as social action";
         // Solr DisMax query parser does not like this
        String apostrophed = "+\"miller\" +\"genre\" +\"as\" +\"social\" +\"action\"";
        compareHits(simple, false, searchDirect(simple), searchDirect(apostrophed));
    }

    public void testExplicitMust() throws RemoteException, ParseException {
        String simple = "miller genre as social action";
        String must = "+miller +genre +as +social +action"; // Solr DisMax query parser does not like this
        compareHits(simple, false, searchDirect(simple), searchDirect(must));
    }

    public void testRewriteWeightsDirect() throws RemoteException, ParseException {
        String query = "foo^2 bar^3";
        checkQueryDirect(query);
    }

}
