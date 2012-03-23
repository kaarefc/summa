/**  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package dk.statsbiblioteket.summa.support.summon.search;

import dk.statsbiblioteket.summa.common.configuration.Configuration;
import dk.statsbiblioteket.summa.common.lucene.index.IndexUtils;
import dk.statsbiblioteket.summa.common.util.SimplePair;
import dk.statsbiblioteket.summa.search.SearchNode;
import dk.statsbiblioteket.summa.search.SearchNodeFactory;
import dk.statsbiblioteket.summa.search.api.Request;
import dk.statsbiblioteket.summa.search.api.Response;
import dk.statsbiblioteket.summa.search.api.ResponseCollection;
import dk.statsbiblioteket.summa.search.api.document.DocumentKeys;
import dk.statsbiblioteket.summa.search.api.document.DocumentResponse;
import dk.statsbiblioteket.summa.support.api.LuceneKeys;
import dk.statsbiblioteket.summa.support.harmonise.AdjustingSearchNode;
import dk.statsbiblioteket.summa.support.harmonise.HarmoniseTestHelper;
import dk.statsbiblioteket.summa.support.harmonise.InteractionAdjuster;
import dk.statsbiblioteket.summa.support.harmonise.QueryRewriter;
import dk.statsbiblioteket.util.Strings;
import dk.statsbiblioteket.util.qa.QAInfo;
import dk.statsbiblioteket.util.xml.DOM;
import junit.framework.Test;
import junit.framework.TestSuite;
import junit.framework.TestCase;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import javax.xml.transform.TransformerException;
import java.io.*;
import java.rmi.RemoteException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@QAInfo(level = QAInfo.Level.NORMAL,
        state = QAInfo.State.IN_DEVELOPMENT,
        author = "te")
public class SummonSearchNodeTest extends TestCase {
    private static Log log = LogFactory.getLog(SummonSearchNodeTest.class);

    public SummonSearchNodeTest(String name) {
        super(name);
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
    }

    public static Test suite() {
        return new TestSuite(SummonSearchNodeTest.class);
    }

    public void testBasicSearch() throws RemoteException {
        log.debug("Creating SummonSearchNode");
        SummonSearchNode summon = SummonTestHelper.createSummonSearchNode();
//        summon.open(""); // Fake open for setting permits
        ResponseCollection responses = new ResponseCollection();
        Request request = new Request();
        request.put(DocumentKeys.SEARCH_QUERY, "foo");
        request.put(DocumentKeys.SEARCH_COLLECT_DOCIDS, true);
        log.debug("Searching");
        summon.search(request, responses);
        log.debug("Finished searching");
        //System.out.println(responses.toXML());
        assertTrue("The result should contain at least one record", responses.toXML().contains("<record score"));
        assertTrue("The result should contain at least one tag", responses.toXML().contains("<tag name"));
    }

    public void testShortFormat() throws RemoteException {
        Configuration conf = SummonTestHelper.getDefaultSummonConfiguration();
        conf.set(SummonResponseBuilder.CONF_SHORT_DATE, true);

        log.debug("Creating SummonSearchNode");
        SummonSearchNode summon = new SummonSearchNode(conf);
//        summon.open(""); // Fake open for setting permits
        final Pattern DATEPATTERN = Pattern.compile(
            "<dc:date>(.+?)</dc:date>", Pattern.DOTALL);
        Request request = new Request();
        request.put(DocumentKeys.SEARCH_QUERY, "foo");
        request.put(DocumentKeys.SEARCH_RESULT_FIELDS, "shortformat");
        request.put(DocumentKeys.SEARCH_COLLECT_DOCIDS, true);
        List<String> dates= getPattern(summon, request, DATEPATTERN);
        assertTrue("There should be at least 1 extracted date",
                   dates.size() > 0);
        for (String date: dates) {
            assertTrue("the returned dates should be of length 4 or less, got '" + date + "'", date.length() <= 4);
        }
//        System.out.println("Got dates:\n" + Strings.join(dates, ", "));
    }

    public void testIDSearch() throws IOException, TransformerException {
        String ID = "summon_FETCH-gale_primary_2105957371";

        log.debug("Creating SummonSearchNode");
        SearchNode summon = SummonTestHelper.createSummonSearchNode(true);
        Request req = new Request(
            DocumentKeys.SEARCH_QUERY, "recordID:\"" + ID + "\"",
            DocumentKeys.SEARCH_MAX_RECORDS, 1,
            DocumentKeys.SEARCH_COLLECT_DOCIDS, false);
        List<String> ids = getAttributes(summon, req, "id");
        assertTrue("There should be at least 1 result", ids.size() >= 1);
    }

    public void testGetField() throws IOException, TransformerException {
        String ID = "summon_FETCH-gale_primary_2105957371";

        log.debug("Creating SummonSearchNode");
        SearchNode summon = SummonTestHelper.createSummonSearchNode(true);
        String fieldName = "shortformat";
        String field = getField(summon, ID, fieldName);
        assertTrue("The field '" + fieldName + "' from ID '" + ID + "' should have content",
                   field != null && !"".equals(field));
//        System.out.println("'" + field + "'");
    }

    /* This is equivalent to SearchWS#getField */
    private String getField(SearchNode searcher, String id, String fieldName) throws IOException, TransformerException {
        String retXML;

        Request req = new Request();
        req.put(DocumentKeys.SEARCH_QUERY, "ID:\"" + id + "\"");
        req.put(DocumentKeys.SEARCH_MAX_RECORDS, 1);
        req.put(DocumentKeys.SEARCH_COLLECT_DOCIDS, false);

        ResponseCollection res = new ResponseCollection();
        searcher.search(req, res);
//        System.out.println(res.toXML());
        Document dom = DOM.stringToDOM(res.toXML());
        Node subDom = DOM.selectNode(
            dom, "/responsecollection/response/documentresult/record/field[@name='" + fieldName + "']");
        retXML = DOM.domToString(subDom);
        return retXML;
    }


    public void testMoreLikeThis() throws RemoteException {
        SummonSearchNode summon = SummonTestHelper.createSummonSearchNode();
        long standard = getHits(summon, DocumentKeys.SEARCH_QUERY, "foo");
        assertTrue("A search for 'foo' should give hits", standard > 0);

        long mlt = getHits(summon, DocumentKeys.SEARCH_QUERY, "foo", LuceneKeys.SEARCH_MORELIKETHIS_RECORDID, "bar");
        assertEquals("A search with a MoreLikeThis ID should not give hits", 0, mlt);

    }

    public void testColonSearch() throws RemoteException {
        final String OK = "FETCH-proquest_dll_14482952011";
        final String PROBLEM = "FETCH-doaj_primary_oai:doaj-articles:932b6445ce452a2b2a544189863c472e1";
        performSearch("ID:\"" + OK + "\"");
        performSearch("ID:\"" + PROBLEM + "\"");
    }

    private void performSearch(String query) throws RemoteException {
        log.debug("Creating SummonSearchNode");
        SummonSearchNode summon = SummonTestHelper.createSummonSearchNode();
        ResponseCollection responses = new ResponseCollection();
        Request request = new Request();
        request.put(DocumentKeys.SEARCH_QUERY, query);
        request.put(DocumentKeys.SEARCH_COLLECT_DOCIDS, true);
        log.debug("Searching");
        summon.search(request, responses);
        log.debug("Finished searching");
//        System.out.println(responses.toXML());
        assertTrue("The result should contain at least one record", responses.toXML().contains("<record score"));
    }

    public void testFacetOrder() throws RemoteException {
        Configuration conf = SummonTestHelper.getDefaultSummonConfiguration();
        conf.set(DocumentKeys.SEARCH_COLLECT_DOCIDS, true);
            //SummonSearchNode.CONF_SOLR_FACETS, ""

        log.debug("Creating SummonSearchNode");
        SummonSearchNode summon = new SummonSearchNode(conf);
//        summon.open(""); // Fake open for setting permits
        ResponseCollection responses = new ResponseCollection();
        Request request = new Request();
        request.put(DocumentKeys.SEARCH_QUERY, "foo");
        request.put(DocumentKeys.SEARCH_COLLECT_DOCIDS, true);
        log.debug("Searching");
        summon.search(request, responses);
        log.debug("Finished searching");
        List<String> facets = getFacetNames(responses);
        List<String> expected = new ArrayList<String>(Arrays.asList(SummonSearchNode.DEFAULT_SUMMON_FACETS.split(" ?, ?")));
        for (int i = expected.size()-1 ; i >= 0 ; i--) {
            if (!facets.contains(expected.get(i))) {
                expected.remove(i);
            }
        }
        assertEquals("The order of the facets should be correct",
                     Strings.join(expected, ", "), Strings.join(facets, ", "));

//        System.out.println(responses.toXML());
//        System.out.println(Strings.join(facets, ", "));
    }

    public void testSpecificFacets() throws RemoteException {
        Configuration conf = SummonTestHelper.getDefaultSummonConfiguration();
        conf.set(DocumentKeys.SEARCH_COLLECT_DOCIDS, true);
        conf.set(SummonSearchNode.CONF_SOLR_FACETS, "SubjectTerms");

        log.debug("Creating SummonSearchNode");
        SummonSearchNode summon = new SummonSearchNode(conf);
        ResponseCollection responses = new ResponseCollection();
        Request request = new Request();
        request.put(DocumentKeys.SEARCH_QUERY, "foo");
        request.put(DocumentKeys.SEARCH_COLLECT_DOCIDS, true);
        log.debug("Searching");
        summon.search(request, responses);
        log.debug("Finished searching");
        List<String> facets = getFacetNames(responses);
        assertEquals("The number of facets should be correct", 1, facets.size());
        assertEquals("The returned facet should be correct",
                     "SubjectTerms", Strings.join(facets, ", "));
    }

    public void testNegativeFacets() throws RemoteException {
        final String QUERY = "foo fighters NOT limits NOT (boo OR bam)";
        final String FACET = "SubjectTerms:\"united states\"";
        SummonSearchNode summon = SummonTestHelper.createSummonSearchNode();
        assertHits("There should be at least one hit for positive faceting",
                   summon,
                   DocumentKeys.SEARCH_QUERY, QUERY,
                   DocumentKeys.SEARCH_FILTER, FACET);
        assertHits("There should be at least one hit for parenthesized positive faceting", summon,
                   DocumentKeys.SEARCH_QUERY, QUERY,
                   DocumentKeys.SEARCH_FILTER, "(" + FACET + ")");
        assertHits("There should be at least one hit for filter with pure negative faceting", summon,
                   DocumentKeys.SEARCH_QUERY, QUERY,
                   DocumentKeys.SEARCH_FILTER_PURE_NEGATIVE, "true",
                   DocumentKeys.SEARCH_FILTER, "NOT " + FACET);
        summon.close();
    }

    // summon used to support pure negative filters (in 2011) but apparently does not with the 2.0.0-API.
    // If they change their stance on the issue, we want to swith back to using pure negative filters, as it
    // does not affect ranking.
    public void testNegativeFacetsSupport() throws RemoteException {
        final String QUERY = "foo fighters NOT limits NOT (boo OR bam)";
        final String FACET = "SubjectTerms:\"united states\"";
        Configuration conf = SummonTestHelper.getDefaultSummonConfiguration();
        conf.set(SummonSearchNode.CONF_SUPPORTS_PURE_NEGATIVE_FILTERS, true);
        SummonSearchNode summon = new SummonSearchNode(conf);
        assertEquals("There should be zero hits for filter with assumed pure negative faceting support", 0,
                     getHits(summon,
                             DocumentKeys.SEARCH_QUERY, QUERY,
                             DocumentKeys.SEARCH_FILTER_PURE_NEGATIVE, "true",
                             DocumentKeys.SEARCH_FILTER, "NOT " + FACET));
        summon.close();
    }

    public void testQueryWithNegativeFacets() throws RemoteException {
        final String QUERY = "foo";
        final String FACET = "SubjectTerms:\"analysis\"";
        SummonSearchNode summon = SummonTestHelper.createSummonSearchNode();
        assertHits("There should be at least one hit for positive faceting", summon,
                   DocumentKeys.SEARCH_QUERY, QUERY,
                   DocumentKeys.SEARCH_FILTER, FACET);
        assertHits("There should be at least one hit for query with negative facet", summon,
                   DocumentKeys.SEARCH_QUERY, QUERY,
                   DocumentKeys.SEARCH_FILTER_PURE_NEGATIVE, Boolean.TRUE.toString(),
                   DocumentKeys.SEARCH_FILTER, "NOT " + FACET);
        summon.close();
    }

    private void assertHits(
        String message, SearchNode searcher, String... queries)
                                                        throws RemoteException {
        long hits = getHits(searcher, queries);
        assertTrue(message + ". Hits == " + hits, hits > 0);
    }

    public void testSortedSearch() throws RemoteException {
        Configuration conf = SummonTestHelper.getDefaultSummonConfiguration();
        conf.set(InteractionAdjuster.CONF_ADJUST_DOCUMENT_FIELDS, "sort_year_asc - PublicationDate");

        log.debug("Creating SummonSearchNode");
        SummonSearchNode summon = new SummonSearchNode(conf);
//        summon.open(""); // Fake open for setting permits
        ResponseCollection responses = new ResponseCollection();
        Request request = new Request();
        request.put(DocumentKeys.SEARCH_QUERY, "sb");
        request.put(DocumentKeys.SEARCH_SORTKEY, "PublicationDate");
//        request.put(DocumentKeys.SEARCH_REVERSE, true);
        request.put(DocumentKeys.SEARCH_COLLECT_DOCIDS, true);
        log.debug("Searching");
        summon.search(request, responses);
        log.debug("Finished searching");
        List<String> sortValues = getAttributes(summon, request, "sortValue");
        String lastValue = null;
        for (String sortValue: sortValues) {
            assertTrue("The sort values should be in unicode order but was " + Strings.join(sortValues, ", "),
                      lastValue == null || lastValue.compareTo(sortValue) <= 0);
//            System.out.println(lastValue + " vs " + sortValue + ": " + (lastValue == null ? 0 : lastValue.compareTo(sortValue)));
            lastValue = sortValue;
        }
        log.debug("Test passed with sort values\n"
                  + Strings.join(sortValues, "\n"));
    }

    public void testSortedDate() throws RemoteException {
        Configuration conf = SummonTestHelper.getDefaultSummonConfiguration();
        conf.set(InteractionAdjuster.CONF_ADJUST_DOCUMENT_FIELDS, "sort_year_asc - PublicationDate");

        log.debug("Creating SummonSearchNode");
        SummonSearchNode summon = new SummonSearchNode(conf);
//        summon.open(""); // Fake open for setting permits
        Request request = new Request();
        request.put(DocumentKeys.SEARCH_QUERY, "dolphin whale");
        request.put(DocumentKeys.SEARCH_SORTKEY, "PublicationDate");
//        request.put(DocumentKeys.SEARCH_REVERSE, true);
        request.put(DocumentKeys.SEARCH_COLLECT_DOCIDS, true);
        log.debug("Searching");
        List<String> fields = getField(summon, request, "PublicationDate_xml_iso");
        log.debug("Finished searching");
        String lastValue = null;
        for (String sortValue: fields) {
            assertTrue("The field values should be in unicode order but was " + Strings.join(fields, ", "),
                      lastValue == null || lastValue.compareTo(sortValue) <= 0);
//            System.out.println(lastValue + " vs " + sortValue + ": " + (lastValue == null ? 0 : lastValue.compareTo(sortValue)));
            lastValue = sortValue;
        }
        log.debug("Test passed with field values\n" + Strings.join(fields, "\n"));
    }

    public void testSortedSearchRelevance() throws RemoteException {
        Configuration conf = SummonTestHelper.getDefaultSummonConfiguration();
        conf.set(InteractionAdjuster.CONF_ADJUST_DOCUMENT_FIELDS, "sort_year_asc - PublicationDate");

        log.debug("Creating SummonSearchNode");
        SummonSearchNode summon = new SummonSearchNode(conf);
//        summon.open(""); // Fake open for setting permits
        ResponseCollection responses = new ResponseCollection();
        Request request = new Request();
        request.put(DocumentKeys.SEARCH_QUERY, "foo");
        request.put(DocumentKeys.SEARCH_SORTKEY, DocumentKeys.SORT_ON_SCORE);
        request.put(DocumentKeys.SEARCH_COLLECT_DOCIDS, true);
        log.debug("Searching");
        summon.search(request, responses);
        log.debug("Finished searching");
        List<String> ids = getAttributes(summon, request, "id");
        assertTrue("There should be some hits", ids.size() > 0);
    }

    public void testPaging() throws RemoteException {
        SummonSearchNode summon = SummonTestHelper.createSummonSearchNode();
//        summon.open(""); // Fake open for setting permits
        List<String> ids0 = getAttributes(
            summon, new Request(
            DocumentKeys.SEARCH_QUERY, "foo",
            DocumentKeys.SEARCH_MAX_RECORDS, 20,
            DocumentKeys.SEARCH_START_INDEX, 0),
            "id");
        List<String> ids1 = getAttributes(
            summon, new Request(
            DocumentKeys.SEARCH_QUERY, "foo",
            DocumentKeys.SEARCH_MAX_RECORDS, 20,
            DocumentKeys.SEARCH_START_INDEX, 20),
            "id");

        assertNotEquals("The hits should differ from page 0 and 1",
                        Strings.join(ids0, ", "), Strings.join(ids1, ", "));
    }

    private void assertNotEquals(String message, String expected, String actual) {
        assertFalse(message + ".\nExpected: " + expected + "\nActual:   " + actual,
                    expected.equals(actual));
    }

    private List<String> getAttributes(SearchNode searcher, Request request, String attributeName)
                                                                                                throws RemoteException {
        final Pattern IDPATTERN = Pattern.compile("<record.*?" + attributeName + "=\"(.+?)\".*?>", Pattern.DOTALL);
        return getPattern(searcher, request, IDPATTERN);
/*        ResponseCollection responses = new ResponseCollection();
        searcher.search(request, responses);
        responses.iterator().next().merge(responses.iterator().next());
        String[] lines = responses.toXML().split("\n");
        List<String> result = new ArrayList<String>();
        for (String line: lines) {
            Matcher matcher = IDPATTERN.matcher(line);
            if (matcher.matches()) {
                result.add(matcher.group(1));
            }
        }
        return result;*/
    }

    private List<String> getField(SearchNode searcher, Request request, String fieldName) throws RemoteException {
        final Pattern IDPATTERN = Pattern.compile(
            "<field name=\"" + fieldName + "\">(.+?)</field>", Pattern.DOTALL);
        return getPattern(searcher, request, IDPATTERN);
    }

    private List<String> getFacetNames(ResponseCollection responses) {
        Pattern FACET = Pattern.compile(".*<facet name=\"(.+?)\">");
        List<String> result = new ArrayList<String>();
        String[] lines = responses.toXML().split("\n");
        for (String line : lines) {
            Matcher matcher = FACET.matcher(line);
            if (matcher.matches()) {
                result.add(matcher.group(1));
            }
        }
        return result;
    }

    private List<String> getPattern(SearchNode searcher, Request request, Pattern pattern) throws RemoteException {
        ResponseCollection responses = new ResponseCollection();
        searcher.search(request, responses);
        responses.iterator().next().merge(responses.iterator().next());
        String xml = responses.toXML();
        Matcher matcher = pattern.matcher(xml);
        List<String> result = new ArrayList<String>();
        while (matcher.find()) {
            result.add(Strings.join(matcher.group(1).split("\n"), ", "));
        }
        return result;
    }

    public void testRecommendations() throws RemoteException {
        SummonSearchNode summon = SummonTestHelper.createSummonSearchNode();
        ResponseCollection responses = new ResponseCollection();
        Request request = new Request();
        request.put(DocumentKeys.SEARCH_QUERY, "PublicationTitle:jama");
//        request.put(DocumentKeys.SEARCH_QUERY, "+(PublicationTitle:jama OR PublicationTitle:jama) +(ContentType:Article OR ContentType:\"Book Chapter\" OR ContentType:\"Book Review\" OR ContentType:\"Journal Article\" OR ContentType:\"Magazine Article\" OR ContentType:Newsletter OR ContentType:\"Newspaper Article\" OR ContentType:\"Publication Article\" OR ContentType:\"Trade Publication Article\")");
      /*
 \+\(PublicationTitle:jama\ OR\ PublicationTitle:jama\)\ \+\(ContentType:Article\ OR\ ContentType:\"Book\ Chapter\"\ OR\ ContentType:\"Book\ Review\"\ OR\ ContentType:\"Journal\ Article\"\ OR\ ContentType:\"Magazine\ Article\"\ OR\ ContentType:Newsletter\ OR\ ContentType:\"Newspaper\ Article\"\ OR\ ContentType:\"Publication\ Article\"\ OR\ ContentType:\"Trade\ Publication\ Article\"\)
      */
        request.put(DocumentKeys.SEARCH_COLLECT_DOCIDS, true);
        summon.search(request, responses);
//        System.out.println(responses.toXML());
        assertTrue("The result should contain at least one recommendation",
                   responses.toXML().contains("<recommendation "));

/*        responses.clear();
        request.put(DocumentKeys.SEARCH_QUERY, "noobddd");
        summon.search(request, responses);
        System.out.println(responses.toXML());
  */
    }

    public void testReportedTiming() throws RemoteException {
        SummonSearchNode summon = SummonTestHelper.createSummonSearchNode();
        ResponseCollection responses = new ResponseCollection();
        Request request = new Request();
        request.put(DocumentKeys.SEARCH_QUERY, "foo");
        summon.search(request, responses);
        Integer rawTime = getTiming(responses, "summon.rawcall");
        assertNotNull("There should be raw time", rawTime);
        Integer reportedTime = getTiming(responses, "summon.reportedtime");
        assertNotNull("There should be reported time", reportedTime);
        assertTrue("The reported time (" + reportedTime + ") should be lower than the raw time (" + rawTime + ")",
                   reportedTime <= rawTime);
        log.debug("Timing raw=" + rawTime + ", reported=" + reportedTime);
    }

    private Integer getTiming(ResponseCollection responses, String key) {
        String[] timings = responses.getTiming().split("[|]");
        for (String timing: timings) {
            String[] tokens = timing.split(":");
            if (tokens.length == 2 && key.equals(tokens[0])) {
                return Integer.parseInt(tokens[1]);
            }
        }
        return null;
    }

    public void testFilterVsQuery() throws RemoteException {
        SummonSearchNode summon = SummonTestHelper.createSummonSearchNode();
        long qHitCount = getHits(summon, DocumentKeys.SEARCH_QUERY, "PublicationTitle:jama");
        long fHitCount = getHits(summon, DocumentKeys.SEARCH_FILTER, "PublicationTitle:jama");

        assertTrue("The filter hit count " + fHitCount + " should differ from query hit count " + qHitCount
                   + " by less than 100",
                   Math.abs(fHitCount - qHitCount) < 100);
    }

    public void testFilterVsQuery2() throws RemoteException {
        SummonSearchNode summon = SummonTestHelper.createSummonSearchNode();
        long qHitCount = getHits(
            summon,
            DocumentKeys.SEARCH_QUERY, "PublicationTitle:jama",
            DocumentKeys.SEARCH_FILTER, "old");
        long fHitCount = getHits(
            summon,
            DocumentKeys.SEARCH_FILTER, "PublicationTitle:jama",
            DocumentKeys.SEARCH_QUERY, "old");

        assertTrue("The filter(old) hit count " + fHitCount + " should differ less than 100 from query(old) hit count "
                   + qHitCount + " as summon API 2.0.0 does field expansion on filters",
                   Math.abs(fHitCount - qHitCount) < 100);
        // This was only true in the pre-API 2.0.0. Apparently the new API does expands default fields for filters
//        assertTrue("The filter(old) hit count " + fHitCount + " should differ from query(old) hit count " + qHitCount
//                   + " by more than 100 as filter query apparently does not query parse with default fields",
//                   Math.abs(fHitCount - qHitCount) > 100);
    }

    public void testFilterVsQuery3() throws RemoteException {
        SummonSearchNode summon = SummonTestHelper.createSummonSearchNode();
        long qCombinedHitCount = getHits(
            summon,
            DocumentKeys.SEARCH_QUERY,
            "PublicationTitle:jama AND Language:English");
        long qHitCount = getHits(
            summon,
            DocumentKeys.SEARCH_QUERY, "PublicationTitle:jama",
            DocumentKeys.SEARCH_FILTER, "(Language:English)");
        long fHitCount = getHits(
            summon,
            DocumentKeys.SEARCH_FILTER, "PublicationTitle:jama",
            DocumentKeys.SEARCH_QUERY, "Language:English");

        assertTrue("The filter(old) hit count " + fHitCount + " should differ"
                   + " from query(old) hit count " + qHitCount
                   + " by less than 100. Combined hit count for query is "
                   + qCombinedHitCount,
                   Math.abs(fHitCount - qHitCount) < 100);
    }

    private long getHits(SearchNode searcher, String... arguments)
                                                        throws RemoteException {
        String HITS_PATTERN = "(?s).*hitCount=\"([0-9]*)\".*";
        ResponseCollection responses = new ResponseCollection();
        searcher.search(new Request(arguments), responses);
        if (!Pattern.compile(HITS_PATTERN).matcher(responses.toXML()).
            matches()) {
            return 0;
        }
        String hitsS = responses.toXML().replaceAll(HITS_PATTERN, "$1");
        return "".equals(hitsS) ? 0L : Long.parseLong(hitsS);
    }

    public void testCustomParams() throws RemoteException {
        final String QUERY = "reactive arthritis yersinia lassen";

        Configuration confInside = SummonTestHelper.getDefaultSummonConfiguration();
        Configuration confOutside = SummonTestHelper.getDefaultSummonConfiguration();
        confOutside.set(SummonSearchNode.CONF_SOLR_PARAM_PREFIX + "s.ho",
                        new ArrayList<String>(Arrays.asList("false"))
        );

        Request request = new Request();
        request.put(DocumentKeys.SEARCH_QUERY, QUERY);
        request.put(DocumentKeys.SEARCH_COLLECT_DOCIDS, true);

        SummonSearchNode summonInside = new SummonSearchNode(confInside);
        ResponseCollection responsesInside = new ResponseCollection();
        summonInside.search(request, responsesInside);

        SummonSearchNode summonOutside = new SummonSearchNode(confOutside);
        ResponseCollection responsesOutside = new ResponseCollection();
        summonOutside.search(request, responsesOutside);

        request.put(SummonSearchNode.CONF_SOLR_PARAM_PREFIX + "s.ho",
                    new ArrayList<String>(Arrays.asList("false")));
        ResponseCollection responsesSearchTweak = new ResponseCollection();
        summonInside.search(request, responsesSearchTweak);

        int countInside = countResults(responsesInside);
        int countOutside = countResults(responsesOutside);
        assertTrue("The number of results for a search for '" + QUERY
                   + "' within holdings (" + confInside + ") should be less "
                   + "that outside holdings (" + confOutside + ")",
                   countInside < countOutside);
        log.info(String.format(
            "The search for '%s' gave %d hits within holdings and %d hits in"
            + " total", QUERY, countInside, countOutside));

        int countSearchTweak = countResults(responsesSearchTweak);
        assertEquals(
            "Query time specification of 's.ho=false' should give the same "
            + "result as configuration time specification of the same",
            countOutside, countSearchTweak);
    }

    public void testConvertRangeQueries() throws RemoteException {
        final String QUERY = "foo bar:[10 TO 20] OR baz:[87 TO goa]";
        Map<String, List<String>> params = new HashMap<String, List<String>>();
        String stripped = new SummonSearchNode(getSummonConfiguration()).convertQuery(QUERY, params);
        assertNotNull("RangeFilter should be defined", params.get("s.rf"));
        List<String> ranges = params.get("s.rf");
        assertEquals("The right number of ranges should be extracted", 2, ranges.size());
        assertEquals("Range #1 should be correct", "bar,10:20", ranges.get(0));
        assertEquals("Range #2 should be correct", "baz,87:goa", ranges.get(1));
        assertEquals("The resulting query should be stripped of ranges", "\"foo\"", stripped);
    }

    public void testConvertRangeQueriesEmpty() throws RemoteException {
        final String QUERY = "bar:[10 TO 20]";
        Map<String, List<String>> params = new HashMap<String, List<String>>();
        String stripped = new SummonSearchNode(getSummonConfiguration()).convertQuery(QUERY, params);
        assertNotNull("RangeFilter should be defined", params.get("s.rf"));
        List<String> ranges = params.get("s.rf");
        assertEquals("The right number of ranges should be extracted", 1, ranges.size());
        assertEquals("Range #1 should be correct", "bar,10:20", ranges.get(0));
        assertNull("The resulting query should be null", stripped);
    }

    private Configuration getSummonConfiguration() {
        return Configuration.newMemoryBased(
            SummonSearchNode.CONF_SUMMON_ACCESSID, "foo",
            SummonSearchNode.CONF_SUMMON_ACCESSKEY, "bar");
    }

    public void testFaultyQuoteRemoval() throws RemoteException {
        final String QUERY = "bar:\"foo:zoo\"";
        Map<String, List<String>> params = new HashMap<String, List<String>>();
        String stripped = new SummonSearchNode(getSummonConfiguration()).convertQuery(QUERY, params);
        assertNull("RangeFilter should not be defined", params.get("s.rf"));
        assertEquals("The resulting query should unchanged", QUERY, stripped);
    }

    // This fails, but as we are really testing Summon here, there is not much
    // we can do about it
    @SuppressWarnings({"UnusedDeclaration"})
    public void disabledtestCounts() throws RemoteException {
  //      final String QUERY = "reactive arthritis yersinia lassen";
        final String QUERY = "author:(Helweg Larsen) abuse";

        Request request = new Request();
        request.addJSON(
            "{search.document.query:\"" + QUERY + "\", " + "summonparam.s.ps:\"15\", summonparam.s.ho:\"false\"}");
//        String r1 = request.toString(true);

        SummonSearchNode summon = SummonTestHelper.createSummonSearchNode();
        ResponseCollection responses = new ResponseCollection();
        summon.search(request, responses);
        int count15 = countResults(responses);

        request.clear();
        request.addJSON(
            "{search.document.query:\"" + QUERY + "\", " + "summonparam.s.ps:\"30\", summonparam.s.ho:\"false\"}");
  //      String r2 = request.toString(true);
        responses.clear();
        summon.search(request, responses);
        int count20 = countResults(responses);
/*
        request.clear();
        request.put(DocumentKeys.SEARCH_QUERY, QUERY);
        request.put(DocumentKeys.SEARCH_COLLECT_DOCIDS, false);
        request.put(SummonSearchNode.CONF_SOLR_PARAM_PREFIX + "s.ho",
                    new ArrayList<String>(Arrays.asList("false")));
        request.put(DocumentKeys.SEARCH_MAX_RECORDS, 15);
        String rOld15 = request.toString(true);
        responses.clear();
        summon.search(request, responses);
        int countOld15 = countResults(responses);

        request.clear();
        request.put(DocumentKeys.SEARCH_QUERY, QUERY);
        request.put(DocumentKeys.SEARCH_COLLECT_DOCIDS, false);
        request.put(SummonSearchNode.CONF_SOLR_PARAM_PREFIX + "s.ho",
                    new ArrayList<String>(Arrays.asList("false")));
        request.put(DocumentKeys.SEARCH_MAX_RECORDS, 20);
        String rOld20 = request.toString(true);
        responses.clear();
        summon.search(request, responses);
        int countOld20 = countResults(responses);
  */
//        System.out.println("Request 15:  " + r1 + ": " + count15);
//        System.out.println("Request 20:  " + r2 + ": " + count20);
//        System.out.println("Request O15: " + rOld15 + ": " + countOld15);
//        System.out.println("Request O20: " + rOld20 + ": " + countOld20);
        assertEquals("The number of hits should not be affected by page size", count15, count20);
    }

    // Author can be returned in the field Author_xml (primary) and Author (secondary). If both fields are present,
    // Author should be ignored.
    public void testAuthorExtraction() throws IOException {

    }

    private int countResults(ResponseCollection responses) {
        for (Response response: responses) {
            if (response instanceof DocumentResponse) {
                return (int)((DocumentResponse)response).getHitCount();
            }
        }
        throw new IllegalArgumentException(
            "No documentResponse in ResponseCollection");
    }

    public void testAdjustingSearcher() throws IOException {
        SimplePair<String, String> credentials = SummonTestHelper.getCredentials();
        Configuration conf = Configuration.newMemoryBased(
            InteractionAdjuster.CONF_IDENTIFIER, "summon",
            InteractionAdjuster.CONF_ADJUST_DOCUMENT_FIELDS, "recordID - ID");
        Configuration inner = conf.createSubConfiguration(AdjustingSearchNode.CONF_INNER_SEARCHNODE);
        inner.set(SearchNodeFactory.CONF_NODE_CLASS, SummonSearchNode.class.getCanonicalName());
        inner.set(SummonSearchNode.CONF_SUMMON_ACCESSID, credentials.getKey());
        inner.set(SummonSearchNode.CONF_SUMMON_ACCESSKEY, credentials.getValue());

        log.debug("Creating adjusting SummonSearchNode");
        AdjustingSearchNode adjusting = new AdjustingSearchNode(conf);
        ResponseCollection responses = new ResponseCollection();
        Request request = new Request(
            //request.put(DocumentKeys.SEARCH_QUERY, "foo");
            DocumentKeys.SEARCH_QUERY, "recursion in string theory",
            DocumentKeys.SEARCH_COLLECT_DOCIDS, true);
        log.debug("Searching");
        adjusting.search(request, responses);
        log.debug("Finished searching");
        // TODO: Add proper test
//        System.out.println(responses.toXML());
    }

    // The ranking should differ when we turn explicit must on/off as it triggers summon DisMax conformance on/off
    public void testExplicitMust() throws IOException {
        final String QUERY = "miller genre as social action";
        ResponseCollection explicitMustResponses = new ResponseCollection();
        {
            Configuration conf = SummonTestHelper.getDefaultSummonConfiguration();
            conf.set(QueryRewriter.CONF_TERSE, false);
            SearchNode summon  = new SummonSearchNode(conf);
            Request request = new Request(
                DocumentKeys.SEARCH_QUERY, QUERY
            );
            summon.search(request, explicitMustResponses);
            summon.close();
        }

        ResponseCollection implicitMustResponses = new ResponseCollection();
        {
            Configuration conf = SummonTestHelper.getDefaultSummonConfiguration();
            conf.set(QueryRewriter.CONF_TERSE, true);
            SearchNode summon  = new SummonSearchNode(conf);
            Request request = new Request(
                DocumentKeys.SEARCH_QUERY, QUERY
            );
            summon.search(request, implicitMustResponses);
            summon.close();
        }
        HarmoniseTestHelper.compareHits(QUERY, false, explicitMustResponses, implicitMustResponses);
    }

    // Author_xml contains the authoritative order for the authors so it should override the non-XML-version
    public void testAuthor_xmlExtraction() throws RemoteException {
        String fieldName = "Author";
        String query = "PQID:821707502";

/*        {
            Configuration conf = SummonTestHelper.getDefaultSummonConfiguration();
            conf.set(SummonResponseBuilder.CONF_XML_OVERRIDES_NONXML, false);
            SearchNode summon = new SummonSearchNode(conf);
            ResponseCollection responses = new ResponseCollection();
            summon.search(new Request(DocumentKeys.SEARCH_QUERY, query), responses);

            DocumentResponse docs = (DocumentResponse)responses.iterator().next();
            for (DocumentResponse.Record record: docs.getRecords()) {
                System.out.println("\n" + record.getId());
                String author = "";
                String author_xml = "";
                for (DocumentResponse.Field field: record.getFields()) {
                    if ("Author".equals(field.getName())) {
                        author = field.getContent().replace("\n", ", ").replace("<h>", "").replace("</h>", "");
                        System.out.println("Author:     " + author);
                    } else if ("Author_xml".equals(field.getName())) {
                        author_xml = field.getContent().replace("\n", ", ");
                        System.out.println("Author_xml: " + author_xml);
                    } else if ("PQID".equals(field.getName())) {
                        System.out.println("PQID: " + field.getContent());
                    }
                }
                if (author.length() != author_xml.length()) {
                    fail("We finally found a difference between Author and Author_xml besides name ordering");
                }
            }
            summon.close();
        }
  */

        { // Old behaviour
            String expected = "Koetse, Willem\n"
                              + "Krebs, Christopher P\n"
                              + "Lindquist, Christine\n"
                              + "Lattimore, Pamela K\n"
                              + "Cowell, Alex J";
            Configuration conf = SummonTestHelper.getDefaultSummonConfiguration();
            conf.set(SummonResponseBuilder.CONF_XML_OVERRIDES_NONXML, false);
            SearchNode summonNonXML = new SummonSearchNode(conf);
            assertFieldContent("XML no override", summonNonXML, query, fieldName, expected, false);
            summonNonXML.close();
        }

        { // New behaviour
            String expected = "Lattimore, Pamela K\n"
                              + "Krebs, Christopher P\n"
                              + "Koetse, Willem\n"
                              + "Lindquist, Christine\n"
                              + "Cowell, Alex J";
            SearchNode summonXML = SummonTestHelper.createSummonSearchNode();
            assertFieldContent("XML override", summonXML, query, fieldName, expected, false);
            summonXML.close();
        }

    }

    private void assertFieldContent(String message, SearchNode searchNode, String query, String fieldName,
                                    String expected, boolean sort) throws RemoteException {
        ResponseCollection responses = new ResponseCollection();
        searchNode.search(new Request(DocumentKeys.SEARCH_QUERY, query), responses);
        DocumentResponse docs = (DocumentResponse)responses.iterator().next();
        assertEquals(message + ". There should only be a single hit", 1, docs.getHitCount());
        boolean found = false;
        for (DocumentResponse.Record record: docs.getRecords()) {
            for (DocumentResponse.Field field: record.getFields()) {
                if (fieldName.equals(field.getName())) {
                    String content = field.getContent();
                    if (sort) {
                        String[] tokens = content.split("\n");
                        Arrays.sort(tokens);
                        content = Strings.join(tokens, "\n");

                    }
                    assertEquals(message + ".The field '" + fieldName + "' should have the right content",
                                 expected, content);
                    found = true;
                }
            }
        }
        if (!found) {
            fail("Unable to locate the field '" + fieldName + "'");
        }
    }

    public void testIDAdjustment() throws IOException {
        SimplePair<String, String> credentials = SummonTestHelper.getCredentials();
        Configuration conf = Configuration.newMemoryBased(
            InteractionAdjuster.CONF_IDENTIFIER, "summon",
            InteractionAdjuster.CONF_ADJUST_DOCUMENT_FIELDS, "recordID - ID");
        Configuration inner = conf.createSubConfiguration(
            AdjustingSearchNode.CONF_INNER_SEARCHNODE);
        inner.set(SearchNodeFactory.CONF_NODE_CLASS, SummonSearchNode.class.getCanonicalName());
        inner.set(SummonSearchNode.CONF_SUMMON_ACCESSID, credentials.getKey());
        inner.set(SummonSearchNode.CONF_SUMMON_ACCESSKEY, credentials.getValue());

        log.debug("Creating adjusting SummonSearchNode");
        AdjustingSearchNode adjusting = new AdjustingSearchNode(conf);
        Request request = new Request();
        //request.put(DocumentKeys.SEARCH_QUERY, "foo");
        request.put(DocumentKeys.SEARCH_QUERY, "recursion in string theory");
        request.put(DocumentKeys.SEARCH_COLLECT_DOCIDS, true);
        List<String> ids = getAttributes(adjusting, request, "id");
        assertTrue("There should be at least one ID", ids.size() > 0);

        request.clear();
        request.put(DocumentKeys.SEARCH_QUERY, IndexUtils.RECORD_FIELD + ":\"" + ids.get(0) + "\"");
        List<String> researchIDs = getAttributes(adjusting, request, "id");
        assertTrue("There should be at least one hit for a search for ID '"
                   + ids.get(0) + "'", researchIDs.size() > 0);
    }

    // TODO: "foo:bar zoo"

}
