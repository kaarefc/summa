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
package dk.statsbiblioteket.summa.support.lucene.search.sort;

import dk.statsbiblioteket.summa.common.util.StringTracker;
import dk.statsbiblioteket.util.Profiler;
import dk.statsbiblioteket.util.Strings;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Tests multiple sort-implementations for Lucene for correctness.
 */
public class MultipassSortComparatorTest extends TestCase {
    private static Log log = LogFactory.getLog(MultipassSortComparatorTest.class);
    public MultipassSortComparatorTest(String name) {
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
        return new TestSuite(MultipassSortComparatorTest.class);
    }

    public void testBasicSort() throws Exception {
        List<String> actual = SortHelper.getSortResult(
                "all:all",
                SortHelper.BASIC_TERMS, new SortHelper.SortFactory() {
            @Override
            Sort getSort(IndexReader reader) {
                return new Sort(new SortField(SortHelper.SORT_FIELD, SortField.STRING));
            }
        });
        String[] expected = Arrays.copyOf(
                SortHelper.BASIC_TERMS, SortHelper.BASIC_TERMS.length);
        Arrays.sort(expected);
        assertEquals("The returned order should be correct",
                     Strings.join(expected, ", "), Strings.join(actual, ", "));
    }

    public void testBasicSortNull() throws Exception {
        List<String> actual = SortHelper.getSortResult(
                "all:all",
                SortHelper.TRICKY_TERMS, new SortHelper.SortFactory() {
            @Override
            Sort getSort(IndexReader reader) {
                return new Sort(new SortField(SortHelper.SORT_FIELD, SortField.STRING));
            }
        });
        String[] expected = Arrays.copyOf(
                SortHelper.TRICKY_TERMS, SortHelper.TRICKY_TERMS.length);
        SortHelper.nullSort(expected);
        assertEquals("The returned order should be correct",
                     Strings.join(expected, ", "), Strings.join(actual, ", "));
    }

    public void testLuceneSortComparator() throws Exception {
        List<String> actual = SortHelper.getSortResult(
                "all:all",
                SortHelper.BASIC_TERMS, new SortHelper.SortFactory() {
            @Override
            Sort getSort(IndexReader reader) throws IOException {
                return new Sort(new SortField(
                        SortHelper.SORT_FIELD, new Locale("da")));
            }
        });
        String[] expected = Arrays.copyOf(
                SortHelper.BASIC_TERMS, SortHelper.BASIC_TERMS.length);
        Arrays.sort(expected);
        assertEquals("The returned order should be correct",
                     Strings.join(expected, ", "), Strings.join(actual, ", "));
    }

    public void testBasicLocalStaticSortComparator() throws Exception {
        List<String> actual = SortHelper.getSortResult(
                "all:all",
                SortHelper.BASIC_TERMS, new SortHelper.SortFactory() {
            @Override
            Sort getSort(IndexReader reader) throws IOException {
                return new Sort(new SortField(
                        SortHelper.SORT_FIELD,
                        new LocalStaticSortComparator("da")));
            }
        });
        String[] expected = Arrays.copyOf(
                SortHelper.BASIC_TERMS, SortHelper.BASIC_TERMS.length);
        Arrays.sort(expected);
        assertEquals("The returned order should be correct",
                     Strings.join(expected, ", "), Strings.join(actual, ", "));
    }

    public void testTrickyLocalStaticSortComparator() throws Exception {
        List<String> actual = SortHelper.getSortResult(
                "all:all",
                SortHelper.TRICKY_TERMS, new SortHelper.SortFactory() {
            @Override
            Sort getSort(IndexReader reader) throws IOException {
                return new Sort(new SortField(
                        SortHelper.SORT_FIELD,
                        new LocalStaticSortComparator("da")));
            }
        });
        String[] EXPECTED = {
                "a aa", "a be", "abe", "bar", "foo", "moo moo", "z",
                "Ægir", "ægir", "Ødis", null, null, null};

        assertEquals("The returned order should be correct",
                     Strings.join(EXPECTED, ", "), Strings.join(actual, ", "));
    }

    public void testMultipassSingleElementOnHeap() throws Exception {
        testMultipassSpecificHeap(10); // 1 char
    }

    public void testMultipass2ElementsOnHeap() throws Exception {
        testMultipassSpecificHeap(StringTracker.
                SINGLE_ENTRY_OVERHEAD * 2 + 10); // ~2 chars
    }

    public void testMultipassAllElementsOnHeap() throws Exception {
        testMultipassSpecificHeap(Integer.MAX_VALUE);
    }

    public void testMultipassSpecificHeap(final int heap)
                                                              throws Exception {
        List<String> actual = SortHelper.getSortResult(
                "all:all",
                SortHelper.BASIC_TERMS, new SortHelper.SortFactory() {
            @Override
            Sort getSort(IndexReader reader) throws IOException {
                return new Sort(new SortField(
                        SortHelper.SORT_FIELD,
                        new MultipassSortComparator("da", heap)));
            }
        });
        String[] expected = Arrays.copyOf(
                SortHelper.BASIC_TERMS, SortHelper.BASIC_TERMS.length);
        Arrays.sort(expected);
        assertEquals("The returned order should be correct with heap " + heap,
                     Strings.join(expected, ", "), Strings.join(actual, ", "));
    }

    // Manual test activation with tweaked Xmx
    public void testCreateIndex() throws Exception {
        int TERM_COUNT = 50000;
        int TERM_MAX_LENGTH = 20;
        Profiler profiler = new Profiler();
        SortHelper.createIndex(
            TestSortComparators.makeTerms(TERM_COUNT, TERM_MAX_LENGTH));
        log.info("Build index with " + TERM_COUNT + " documents in "
                           + profiler.getSpendTime());
    }

    public void testCollator() throws Exception {
        MultipassSortComparator sc = new MultipassSortComparator("da", 1000);
        assertTrue("Comparing a with b should work",
                   sc.compare("a", "b") < 0);
        assertTrue("Comparing bkNTeMbjØfUs with mB2 should work",
                   sc.compare("bkNTeMbjØfUs", "mB2") < 0);
        assertTrue("Comparing mB2 with mÆju12Q should work",
                   sc.compare("mB2", "mÆju12Q") < 0);
        assertTrue("Comparing m9Yi)o3Kq with mB2 should work",
                   sc.compare("m9Yi)o3Kq", "mB2") < 0);
    }

    // Manual test activation with tweaked Xmx
    public void testSortLucene() throws Exception {
        File index = new File(System.getProperty("java.io.tmpdir"));
        log.info("Performing standard Lucene sorted search");
        Profiler profiler = new Profiler();
        long lucene = SortHelper.performSortedSearch(
                index, "all:all", 10, TestSortComparators.getLuceneFactory(
                SortHelper.SORT_FIELD));
        log.info(
                "Lucene sort search performed, using " + lucene / 1024
                + " KB in " + profiler.getSpendTime());
        // TODO assert
    }

    // Manual test activation with tweaked Xmx
    public void testSortMultipass() throws Exception {
        File index = new File(System.getProperty("java.io.tmpdir"));
        log.info("Performing initial Multipass sorted search");
        Profiler profiler = new Profiler();
        long lucene = SortHelper.performSortedSearch(
                index, "all:all", 10, TestSortComparators.getMultipassFactory(
                SortHelper.SORT_FIELD,  1000 * 1024));
        log.info(
                "Multipass sort search performed, using " + lucene / 1024
                + " KB in " + profiler.getSpendTime());
        // TODO assert
    }

    public void testMultipassLoops() throws Exception {
        int TERM_COUNT = 45;
        int TERM_MAX_LENGTH = 20;
        int BUFFER = 800;
        String[] terms = TestSortComparators.makeTerms(TERM_COUNT, TERM_MAX_LENGTH);

        Profiler profiler = new Profiler();
        SortHelper.createIndex(terms);
        log.info("Build index with " + TERM_COUNT + " documents in "
                           + profiler.getSpendTime());

        File index = new File(System.getProperty("java.io.tmpdir"));
        profiler = new Profiler();
        SortHelper.SortFactory sf = TestSortComparators.getMultipassFactory(
                SortHelper.SORT_FIELD,  BUFFER);
        long lucene = SortHelper.performSortedSearch(index, "all:all", 10, sf);

        log.info(
                "Multipass sort search performed, using " + lucene / 1024
                + " KB in " + profiler.getSpendTime());
        // tODO assert
                                /*
        IndexSearcher searcher = new IndexSearcher(index.toString());
        Sort multipassSort = sf.getSort(searcher.getIndexReader());
        MultipassSortComparator multipass =
                (MultipassSortComparator)multipassSort.getSort()[0].
                        getFactory().newComparator(
                        searcher.getIndexReader(), SortHelper.SORT_FIELD);
        List<MultipassSortComparator.OrderedString> multiSorted =
               new ArrayList<MultipassSortComparator.OrderedString>(TERM_COUNT);
        int counter = 0;
        for (String term: terms) {
            multiSorted.add(new MultipassSortComparator.OrderedString(
                    term, counter++));
        }
        Collections.sort(multipassSort, multipass.))
        searcher.close();         */
    }

    public void testDualRepeat() throws Exception {
        testSortLuceneRepeat();
        testSortMultipassRepeat();
    }

    public void testSortLuceneRepeat() throws Exception {
        int REPEATS = 5;

        File index = new File(System.getProperty("java.io.tmpdir"));
        Profiler profiler = new Profiler();
        long lucene = SortHelper.performSortedSearch(
                index, "all:all", 10, TestSortComparators.getLuceneFactory(
                SortHelper.SORT_FIELD));
        log.info(
                "Lucene initial sort search performed, using " + lucene / 1024
                + " KB in " + profiler.getSpendTime());

        long searchTime = SortHelper.timeSortedSearch(
                index, "all:all", 10, TestSortComparators.getLuceneFactory(
                SortHelper.SORT_FIELD), REPEATS);
        log.info(
                "Lucene sort search #" + REPEATS + " took "
                + searchTime + " ms");
        // TODO assert
    }

    public void testSortMultipassRepeat() throws Exception {
        int REPEATS = 5;

        File index = new File(System.getProperty("java.io.tmpdir"));

        Profiler profiler = new Profiler();
        long multipass = SortHelper.performSortedSearch(
                index, "all:all", 10,
                TestSortComparators.getMultipassFactory(
                SortHelper.SORT_FIELD, 20 * 1024 * 1024));
        log.info(
                "Multipass initial sort search performed, using "
                + multipass / 1024 + " KB in " + profiler.getSpendTime());

        long searchTime = SortHelper.timeSortedSearch(
                index, "all:all", 10,
                TestSortComparators.getMultipassFactory(
                SortHelper.SORT_FIELD, 100 * 1024 * 1024), REPEATS);
        log.info(
                "Multipass sort search #" + REPEATS + " took "
                + searchTime + " ms");
    }



    // Manual test activation with tweaked Xmx
    public void testSortstatic() throws Exception {
        File index = new File(System.getProperty("java.io.tmpdir"));
        log.info("Performing initial Staticlocal sorted search");
        Profiler profiler = new Profiler();
        long lucene = SortHelper.performSortedSearch(
                index, "all:all", 10, TestSortComparators.getStaticFactory(
                SortHelper.SORT_FIELD));
        log.info(
                "Staticlocal sort search performed, using " + lucene / 1024
                + " KB in " + profiler.getSpendTime());
    }
}