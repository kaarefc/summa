/*
 *  Licensed under the Apache License, Version 2.0 (the "License");
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
package dk.statsbiblioteket.summa.releasetest;

import dk.statsbiblioteket.summa.common.Record;
import dk.statsbiblioteket.summa.common.configuration.Configuration;
import dk.statsbiblioteket.summa.common.configuration.Resolver;
import dk.statsbiblioteket.summa.common.index.IndexDescriptor;
import dk.statsbiblioteket.summa.common.unittest.NoExitTestCase;
import dk.statsbiblioteket.summa.facetbrowser.api.IndexKeys;
import dk.statsbiblioteket.summa.search.IndexWatcher;
import dk.statsbiblioteket.summa.search.SummaSearcherImpl;
import dk.statsbiblioteket.summa.search.api.Request;
import dk.statsbiblioteket.summa.search.api.ResponseCollection;
import dk.statsbiblioteket.summa.search.api.SummaSearcher;
import dk.statsbiblioteket.summa.search.api.document.DocumentKeys;
import dk.statsbiblioteket.summa.storage.api.Storage;
import dk.statsbiblioteket.summa.storage.api.StorageIterator;
import dk.statsbiblioteket.util.Files;
import dk.statsbiblioteket.util.Strings;
import dk.statsbiblioteket.util.qa.QAInfo;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tests a ingest => storage => index => search chain with facets.
 * </p><p>
 * IMPORTANT: Due to problems with releasing JDBC, the tests cannot be run
 * in succession, but must be started one at a time in their own JVM.
 */
@SuppressWarnings({"DuplicateStringLiteralInspection"})
@QAInfo(level = QAInfo.Level.NORMAL,
        state = QAInfo.State.IN_DEVELOPMENT,
        author = "te")
public class FacetTest extends NoExitTestCase {
    private static Log log = LogFactory.getLog(FacetTest.class);

    @Override
    public void setUp () throws Exception {
        super.setUp();
        cleanup();
        SearchTest.INDEX_ROOT.mkdirs();
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
//        cleanup();
    }

    private void cleanup() throws Exception {
        IngestTest.deleteOldStorages();
        if (SearchTest.INDEX_ROOT.exists()) {
            Files.delete(SearchTest.INDEX_ROOT);
        }
    }

    public static Configuration getSearcherConfiguration() throws Exception {
        URL descriptorLocation = Resolver.getURL(
                "data/search/SearchTest_IndexDescriptor.xml");
        assertNotNull("The descriptor location should not be null",
                      descriptorLocation);

        Configuration searcherConf = Configuration.load(
                "data/search/FacetTest_SearchConfiguration.xml");
        assertNotNull("The Facet configuration should not be empty",
                      searcherConf);
        searcherConf.getSubConfiguration(IndexDescriptor.CONF_DESCRIPTOR).
                set(IndexDescriptor.CONF_ABSOLUTE_LOCATION,
                     descriptorLocation.getFile());
                /*
        List<Configuration> subSearcherConfs =
                searcherConf.getSubConfigurations(SearchNodeFactory.CONF_NODES);
        for (Configuration subSearcherConf: subSearcherConfs) {
            if (subSearcherConf.getString(
                    SearchNodeFactory.CONF_NODE_CLASS).equals(
                    LuceneSearchNode.class.getName())) {
                Configuration descConf = subSearcherConf.getSubConfiguration(
                        IndexDescriptor.CONF_DESCRIPTOR);
                log.debug("Updating the location of the IndexDescriptor to "
                          + descriptorLocation.getFile());
                assertTrue("The descriptorlocation should be present",
                           descConf.valueExists(
                                   IndexDescriptor.CONF_ABSOLUTE_LOCATION));
                descConf.set(IndexDescriptor.CONF_ABSOLUTE_LOCATION,
                             descriptorLocation.getFile());
            }
        }         */
        searcherConf.set(IndexWatcher.CONF_INDEX_WATCHER_INDEX_ROOT,
                         SearchTest.INDEX_ROOT.toString());
        return searcherConf;
    }

/* Does not work as the updateIndex-method for SerachTest does not handle facets
   public void testPlainSearch() throws Exception {
        SummaSearcher searcher =
                new SummaSearcherImpl(getSearcherConfiguration());
        SearchTest.testFullSearcher(searcher);
    }*/

    public void testCreateSearcher() throws Exception {
        SummaSearcherImpl searcher =
                new SummaSearcherImpl(getSearcherConfiguration());
        log.debug("Searcher created: " + searcher);
        searcher.close();
    }

    public void testUpdateBlankIndex() throws Exception {
        Storage storage = SearchTest.startStorage();
        log.debug("Storage started");
        updateIndex();
        storage.close();
    }

    public void testIngest() throws Exception {
        Storage storage = SearchTest.startStorage();
        log.debug("Storage started");
        SearchTest.ingest(new File(
                Resolver.getURL("data/search/input/part1").getFile()));
        assertTrue("Hans Jensen data should be ingested",
                    storage.getRecords(
                        Arrays.asList("fagref:hj@example.com"), null).size() == 1);
        storage.close();
    }

    public void testSimpleSearch() throws Exception {
        Storage storage = SearchTest.startStorage();
        SearchTest.ingest(new File(
                Resolver.getURL("data/search/input/part1").getFile()));
        Record hansRecord = storage.getRecord("fagref:hj@example.com", null);
        assertTrue("The fagref Hans should exist in storage",
                   hansRecord != null);
        assertEquals("The Records-count should be correct after first ingest",
                     1, countRecords(storage, "fagref"));

        updateIndex();
        log.debug("Index updated. Creating searcher");
        SummaSearcherImpl searcher =
                new SummaSearcherImpl(getSearcherConfiguration());
        log.debug("Searcher created. Verifying existence of Hans Jensen data");
        SearchTest.verifySearch(searcher, "Hans", 1);
        log.debug("Sample output from search: "
                  + searcher.search(SearchTest.simpleRequest("Hans")).toXML());
        SearchTest.verifySearch(searcher, "Hans", 1);
        log.debug("Second test-search performed with success");
        verifyFacetResult(searcher, "Hans");
        log.debug("All OK. Closing searcher, storage and returning");
        searcher.close();
        storage.close();
    }

    public void testIndexLookup() throws Exception {
        Storage storage = SearchTest.startStorage();
        SearchTest.ingest(new File(
                Resolver.getURL("data/search/input/part1").getFile()));
        Record hansRecord = storage.getRecord("fagref:hj@example.com", null);
        assertTrue("The fagref Hans should exist in storage",
                   hansRecord != null);
        assertEquals("The Records-count should be correct after first ingest",
                     1, countRecords(storage, "fagref"));
        SearchTest.ingest(new File(
                Resolver.getURL("data/search/input/part2").getFile()));

        updateIndex();
        log.debug("Index updated. Creating searcher");
        SummaSearcherImpl searcher =
                new SummaSearcherImpl(getSearcherConfiguration());

        { // Query, minCount 1
            Request request = new Request();
            request.put(IndexKeys.SEARCH_INDEX_QUERY,    "jensen");
            request.put(IndexKeys.SEARCH_INDEX_FIELD,    "author_person");
            request.put(IndexKeys.SEARCH_INDEX_TERM,     "H");
            request.put(IndexKeys.SEARCH_INDEX_DELTA,    0);
            request.put(IndexKeys.SEARCH_INDEX_MINCOUNT, 1);
            ResponseCollection responses = searcher.search(request);
//            System.out.println(responses.toXML());
            assertFalse("The index lookup should have no Jens Hansen entry but"
                        + " had\n" + responses.toXML(),
                        responses.toXML().contains(
                            "<term count=\"0\">Jens Hansen</term>"));
            assertTrue("The index lookup should return 1 for Hans Jensen but "
                       + "got\n" + responses.toXML(),
                       responses.toXML().contains(
                           "<term count=\"1\">Hans Jensen</term>"));
        }

        { // Query, minCount 0
            Request request = new Request();
            request.put(IndexKeys.SEARCH_INDEX_QUERY, "jensen");
            request.put(IndexKeys.SEARCH_INDEX_FIELD, "author_person");
            request.put(IndexKeys.SEARCH_INDEX_TERM, "J");
            request.put(IndexKeys.SEARCH_INDEX_MINCOUNT, 0);
            ResponseCollection responses = searcher.search(request);
            assertTrue("The index lookup should return 0 for Jens Hansen but "
                       + "got\n" + responses.toXML(),
                       responses.toXML().contains(
                           "<term count=\"0\">Jens Hansen</term>"));
  //          System.out.println(responses.toXML());
        }

        { // No query, hit all
            Request request = new Request();
//        request.put(DocumentKeys.SEARCH_QUERY, "*");
            //    request.put(IndexKeys.SEARCH_INDEX_QUERY, "");
            request.put(IndexKeys.SEARCH_INDEX_FIELD, "author_person");
            request.put(IndexKeys.SEARCH_INDEX_TERM, "J");
            ResponseCollection responses = searcher.search(request);
            assertTrue("The index lookup should return at least one term",
                       responses.toXML().contains(
                           "<term count=\"1\">Hans Jensen</term>"));
        //    System.out.println(responses.toXML());
        }

        // TODO: IndexDescriptor-load?
        searcher.close();
        storage.close();
    }


    public static  void verifyFacetResult(
        SummaSearcher searcher, String query) throws IOException {
        verifyFacetResult(searcher, query, true);
    }

    public static  void verifyFacetResult(
        SummaSearcher searcher, String query, boolean shouldExist)
        throws IOException {
        String res = searcher.search(SearchTest.simpleRequest(query)).toXML();
        boolean contains = res.contains("<facet name=\"author\">");
        if ((shouldExist && !contains)) {
            fail("Search for '" + query
                 + "' did not produce any facets. Result was:\n" + res);
        }

        if ((!shouldExist && contains)) {
            fail("Search for '" + query + "' did produce facets when it should "
                 + "not. Result was:\n" + res);
        }
    }

    public void testTwoDocumentsOneHitSearch() throws Exception {
        Storage storage = SearchTest.startStorage();
        SearchTest.ingest(new File(
                Resolver.getURL("data/search/input/part2").getFile()));
        Record gurliRecord = storage.getRecord("fagref:gm@example.com", null);
        assertTrue(gurliRecord != null);
        assertEquals("The Records-count should be correct after first ingest",
                     2, countRecords(storage, "fagref"));

        updateIndex();
        log.debug("Index updated. Creating searcher");
        SummaSearcherImpl searcher =
                new SummaSearcherImpl(getSearcherConfiguration());
        log.debug("Searcher created. Verifying existence of Gurli data");
        SearchTest.verifySearch(searcher, "Gurli", 1);
        log.debug("Sample output from search: "
                  + searcher.search(SearchTest.simpleRequest("Gurli")).toXML());
        SearchTest.verifySearch(searcher, "Gurli", 1);
        SearchTest.verifySearch(searcher, "Jens", 1);
        log.debug("Second test-search performed with success");
        verifyFacetResult(searcher, "Jens");
        verifyFacetResult(searcher, "Gurli");
        log.debug("All OK. Closing searcher, storage and returning");
        searcher.close();
        storage.close();
    }

    public void testThreeFileIngest() throws Exception {
        Storage storage = SearchTest.startStorage();
        SearchTest.ingest(new File(
                Resolver.getURL("data/search/input/part1plus2").getFile()));
        assertEquals("The Records-count should be correct after first ingest",
                     3, countRecords(storage, "fagref"));

        updateIndex();
        Thread.sleep(5000); // Why do we need to do this?
        log.debug("Index updated. Creating searcher");
        SummaSearcherImpl searcher =
                new SummaSearcherImpl(getSearcherConfiguration());
        Thread.sleep(5000); // Why do we need to do this?
        searcher.checkIndex(); // Make double sure
        log.debug("Searcher created");
        for (String name: "Jens Gurli Hans".split(" ")) {
            log.debug(String.format("Verifying existence of %s data", name));
            SearchTest.verifySearch(searcher, name, 1);
            verifyFacetResult(searcher, name);
        }
        searcher.close();
        storage.close();
    }

    public void testTagCounting() throws Exception {
        Storage storage = SearchTest.startStorage();
        SearchTest.ingest(new File(
                Resolver.getURL("data/search/input/partClone").getFile()));
        assertEquals("The Records-count should be correct after first ingest",
                     5, countRecords(storage, "fagref"));

        updateIndex();
        log.debug("Index updated. Creating searcher");
        SummaSearcherImpl searcher =
                new SummaSearcherImpl(getSearcherConfiguration());
        searcher.checkIndex(); // Make double sure
        log.debug("Searcher created");
        for (String name: "Jens Gurli Hans".split(" ")) {
            log.debug(String.format("Verifying existence of %s data", name));
            SearchTest.verifySearch(
                    searcher, name, name.equals("Hans") ? 3 : 1);
            verifyFacetResult(searcher, name);
        }
        log.debug("Result for search for fagref "
                  + searcher.search(SearchTest.simpleRequest(
                "fagekspert")).toXML());
        searcher.close();
        storage.close();
    }

    public void testDualIngest() throws Exception {
        Storage storage = SearchTest.startStorage();
        SearchTest.ingest(new File(
                Resolver.getURL("data/search/input/part1").getFile()));
        assertEquals("The Records-count should be correct after first ingest",
                     1, countRecords(storage, "fagref"));
        SearchTest.ingest(new File(
                Resolver.getURL("data/search/input/part2").getFile()));
        assertEquals("The Records-count should be correct after second ingest",
                     3, countRecords(storage, "fagref"));

        Record hansRecord = storage.getRecord("fagref:hj@example.com", null);
        assertTrue(hansRecord != null);
        Record gurliRecord = storage.getRecord("fagref:gm@example.com", null);
        assertTrue(gurliRecord != null);

        updateIndex();
        Thread.sleep(5000); // Why do we need to do this?
        log.debug("Index updated. Creating searcher");
        SummaSearcherImpl searcher =
                new SummaSearcherImpl(getSearcherConfiguration());
        Thread.sleep(5000); // Why do we need to do this?
        searcher.checkIndex(); // Make double sure
        log.debug("Searcher created");
        for (String name: "Jens Gurli Hans".split(" ")) {
            log.debug(String.format("Verifying existence of %s data", name));
            SearchTest.verifySearch(searcher, name, 1);
            verifyFacetResult(searcher, name);
        }
        searcher.close();
        storage.close();
    }

    private int countRecords(Storage storage, String base) throws IOException {
        long iterKey = storage.getRecordsModifiedAfter(0, base, null);
        Iterator<Record> i = new StorageIterator(storage, iterKey);
        int counter = 0;
        while (i.hasNext()) {
            counter++;
            i.next();
        }
        return counter;
    }

    public void testFacetSearch() throws Exception {
//        ExposedSettings.debug = true;
        log.debug("Getting configuration for searcher");
        Configuration conf = getSearcherConfiguration();
        log.debug("Creating Searcher");
        SummaSearcherImpl searcher = new SummaSearcherImpl(conf);
        log.debug("Searcher created");
        Storage storage = SearchTest.startStorage();
        log.debug("Storage started");
        updateIndex();
        log.debug("Update 1 performed");
        searcher.checkIndex();
        // An empty index should return 0 hits
/*        log.debug("CheckIndex called");
        try {
            String searchResult =
                    searcher.search(SearchTest.simpleRequest("dummy")).toXML();
            // TODO: Check if this is what we want
            fail("An timeout-Exceptions should be thrown with empty index. "
                 + "Instead we got the result\n" + searchResult);
        } catch (RemoteException e) {
            // Expected
        }*/
        SearchTest.ingest(new File(
                Resolver.getURL("data/search/input/part1").getFile()));
        log.debug("Ingest 1 performed");
        updateIndex();
        log.debug("Update 2 performed");
        searcher.checkIndex();
        log.debug("Checkindex after Update 2 performed, verifying...");
        SearchTest.verifySearch(searcher, "Hans", 1);
        verifyFacetResult(searcher, "Hans");
        log.debug("Sample output after initial ingest: "
                  + searcher.search(SearchTest.simpleRequest("fagekspert")).
                toXML());
        log.debug("Adding new material");
        SearchTest.ingest(new File(
                Resolver.getURL("data/search/input/part2").getFile()));
        updateIndex();
        log.debug("Waiting for the searcher to discover the new index");        searcher.checkIndex(); // Make double sure
        log.debug("Verify final index");
        SearchTest.verifySearch(searcher, "Gurli", 1);
        SearchTest.verifySearch(searcher, "Gurli", 1); // Yes, we try again
        SearchTest.verifySearch(searcher, "Hans", 1);
        verifyFacetResult(searcher, "Gurli");
        verifyFacetResult(searcher, "Hans"); // Why is Hans especially a problem?
        log.debug("Sample output from large search: "
                  + searcher.search(SearchTest.simpleRequest("fagekspert")).
                toXML());

        searcher.close();
        storage.close();
    }

    public void testFacetSearchDelete() throws Exception {
        final String HANS = "fagref:hj@example.com";

//        ExposedSettings.debug = true;
        log.debug("Getting configuration for searcher");
        Configuration conf = getSearcherConfiguration();
        log.debug("Creating Searcher");
        SummaSearcherImpl searcher = new SummaSearcherImpl(conf);
        log.debug("Searcher created");
        Storage storage = SearchTest.startStorage();
        log.debug("Storage started");
        updateIndex();
        SearchTest.ingest(new File(
                Resolver.getURL("data/search/input/part1").getFile()));
        SearchTest.ingest(new File(
                Resolver.getURL("data/search/input/part2").getFile()));
        log.debug("Ingest 1+2 performed");
        updateIndex();
        log.debug("Waiting for the searcher to discover the new index");
        searcher.checkIndex(); // Make double sure
        log.debug("Verify index with 3 fagref Records");
        SearchTest.verifySearch(searcher, "Gurli", 1);
        SearchTest.verifySearch(searcher, "Hans", 1);
        verifyFacetResult(searcher, "Gurli");
        verifyFacetResult(searcher, "Hans"); // Why is Hans especially a problem?
        Record hans = storage.getRecord(HANS, null);
        assertNotNull("The Hans Record should exist", hans);
        long originalModTime = hans.getModificationTime();

        log.debug("Sleeping to ensure that progress is properly updated");
        Thread.sleep(1000);

        log.debug("Deleting document");
        hans.setDeleted(true);
        SearchTest.update(hans);
        hans = storage.getRecord(HANS, null);
        long deleteModTime = hans.getModificationTime();
        assertTrue("The Hans-Record should be marked as deleted but was "
                   + hans, hans.isDeleted());
        assertFalse("The modification time for the Hans Records should differ",
                    originalModTime == deleteModTime);
        log.debug("Extracted deleted hans record " + hans.toString(true));
        log.debug("Updating index after update of " + HANS);
        updateIndex();
        searcher.checkIndex(); // Make double sure

        log.debug("Verifying index after update of Hans Jensen");
        SearchTest.verifySearch(searcher, "Gurli", 1);
        SearchTest.verifySearch(searcher, "Hans", 0);

        log.debug("Sample output from large search: "
                  + searcher.search(SearchTest.simpleRequest("fagekspert")).
                toXML());

        searcher.close();
        storage.close();
    }


    public void testExplain() throws Exception {
        Configuration conf = getSearcherConfiguration();
        SummaSearcherImpl searcher = new SummaSearcherImpl(conf);
        Storage storage = SearchTest.startStorage();
        SearchTest.ingest(new File(
                Resolver.getURL("data/search/input/part1").getFile()));
        SearchTest.ingest(new File(
                Resolver.getURL("data/search/input/part2").getFile()));
        updateIndex();
        searcher.checkIndex();
        SearchTest.verifySearch(searcher, "Gurli", 1); // Just checking

        Request request = new Request();
        request.put(DocumentKeys.SEARCH_QUERY, "Gurli");
        request.put(DocumentKeys.SEARCH_EXPLAIN, true);
        log.debug("Sample output from explain search: "
                  + searcher.search(request).toXML());

        searcher.close();
        storage.close();
    }

    public void testSort() throws Exception {
        Configuration conf = getSearcherConfiguration();
        SummaSearcherImpl searcher = new SummaSearcherImpl(conf);
        Storage storage = SearchTest.startStorage();
        SearchTest.ingest(new File(
                Resolver.getURL("data/search/input/part1").getFile()));
        SearchTest.ingest(new File(
                Resolver.getURL("data/search/input/part2").getFile()));
        updateIndex();
        searcher.checkIndex();

        Request request = new Request();
        request.put(DocumentKeys.SEARCH_QUERY, "fagekspert");
        request.put(DocumentKeys.SEARCH_SORTKEY, "sort_title");
        String result = searcher.search(request).toXML();
        String first = Strings.join(getIDs(result), ", ");
        log.debug("IDs from sort_title sort: " + first);

        request.put(DocumentKeys.SEARCH_REVERSE, true);
        result = searcher.search(request).toXML();
        String second = Strings.join(getIDs(result), ", ");
        log.debug("IDs from sort_title reverse: " + second);

        assertFalse(String.format(
                "The first IDs '%s' should be in reverse order of the second "
                + "IDs '%s'", first, second), first.equals(second));
        searcher.close();
        storage.close();
    }

    private Pattern idPattern =
            Pattern.compile("fagref\\:[a-z]+\\@example\\.com");
    private List<String> getIDs(String xml) {
        Matcher matcher = idPattern.matcher(xml);
        List<String> matches = new ArrayList<String>(10);
        while (matcher.find()) {
            matches.add(matcher.group(0));
        }
        return matches;
    }

    public void testFacetBuild() throws Exception {
        Storage storage = SearchTest.startStorage();
        updateIndex();
        storage.close();
    }

    public static void updateIndex() throws Exception {
        Configuration indexConf = Configuration.load(
                "data/search/FacetTest_IndexConfiguration.xml");
        IndexTest.updateIndex(indexConf);
    }
}
