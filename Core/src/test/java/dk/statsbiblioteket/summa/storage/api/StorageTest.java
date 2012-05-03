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
package dk.statsbiblioteket.summa.storage.api;

import dk.statsbiblioteket.summa.common.Record;
import dk.statsbiblioteket.summa.common.configuration.Configuration;
import dk.statsbiblioteket.summa.storage.StorageMonkeyHelper;
import dk.statsbiblioteket.summa.storage.StorageTestBase;
import dk.statsbiblioteket.summa.storage.database.DatabaseStorage;
import dk.statsbiblioteket.util.Logs;
import dk.statsbiblioteket.util.qa.QAInfo;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Test {@link Storage}.
 */
@QAInfo(level = QAInfo.Level.NORMAL,
        state = QAInfo.State.QA_NEEDED,
        author = "hbk")
public class StorageTest extends StorageTestBase {
    /** Local logger. */
    private static Log log = LogFactory.getLog(StorageTest.class);

    public void testGetRecordNoParent() throws IOException {
        Record in = new Record(testId1, testBase1, testContent1);
        in.setParentIds(Arrays.asList("nonexisting"));
        storage.flush(in);

        Record out = storage.getRecord(testId1, null);
        assertNotNull("The extracted record should exist", out);
    }

    public void testGetRecordNoChild() throws IOException {
        Record in = new Record(testId1, testBase1, testContent1);
        in.setChildIds(Arrays.asList("nonexisting"));
        storage.flush(in);

        Record out = storage.getRecord(testId1, null);
        assertNotNull("The extracted record should exist", out);
    }

    public void testGetModifiedAfterAll() throws Exception {
        Record r1 = new Record(testId1, testBase1, testContent1);
        storage.flush(r1);
        storage.flush(new Record(testId2, testBase2, testContent2));
        StorageIterator records = new StorageIterator(storage, storage.getRecordsModifiedAfter(0, null, null));
        assertTrue("The iterator should contain at least one record", records.hasNext());
        records.next();
        assertTrue("The iterator should contain a second record", records.hasNext());
        records.next();
        if (records.hasNext()) {
        	fail("Storage should be depleted after 2 records. Got record " + records.next());
        }
    }

    public void testQueryOptions() throws Exception {
        Record in = new Record(testId1, testBase1, testContent1);
        in.addMeta("foo", "bar");
        in.addMeta("bork", "boo");
        storage.flush(in);

        {
            QueryOptions options = new QueryOptions(null, null, -1, -1, null, new QueryOptions.ATTRIBUTES[]{
                QueryOptions.ATTRIBUTES.ID
            });
            Record out = storage.getRecords(Arrays.asList(testId1), options).get(0);
            assertEquals("Without meta attribute, there should be no meta elements", 0, out.getMeta().size());
        }

        {
            QueryOptions options = new QueryOptions(null, null, -1, -1, null, new QueryOptions.ATTRIBUTES[]{
                QueryOptions.ATTRIBUTES.ID,
                QueryOptions.ATTRIBUTES.META
            });
            Record out = storage.getRecords(Arrays.asList(testId1), options).get(0);
            assertEquals("With meta attribute, there should be meta elements", 2, out.getMeta().size());
            assertEquals(in.getMeta("foo"), out.getMeta("foo"));
            assertEquals(in.getMeta("bork"), out.getMeta("bork"));
            assertFalse("The returned Record should not match completely", in.equals(out));
        }
    }

    /**
     * Test get empty.
     * @throws Exception If error.
     */
    public void testGetEmpty() throws Exception {
        List<Record> recs = storage.getRecords(new ArrayList<String>(), null);
        assertEquals(0, recs.size());
    }

    /**
     * Test get non existing record.
     * @throws Exception If error.
     */
    public void testGetNonExisting() throws Exception {
        List<Record> recs = storage.getRecords(Arrays.asList(testId1), null);
        assertEquals("There should be 0 records to start with. First record was "
                     + (recs.size() > 0 ? recs.get(0) : "N/A"), 0, recs.size());
    }

    /**
     * Test clear empty base.
     * @throws Exception If error.
     */
    public void testClearEmptyBase() throws Exception {
        storage.clearBase(testBase1);
        assertBaseEmpty(testBase1);
    }

    /**
     * Test iteration.
     * @throws Exception If error.
     */
    public void testIteration() throws Exception {
        storage.clearBase(testBase1);
        storage.flush(new Record(testId1, testBase1, testContent1));
        storage.flush(new Record(testId2, testBase1, testContent1));
        assertBaseCount(testBase1, 2);
    }

    /**
     * Test add one.
     * @throws Exception If error.
     */
    public void testAddOne() throws Exception {
        Record rec = new Record(testId1, testBase1, testContent1);
        storage.flush(rec);

        List<Record> recs = storage.getRecords(Arrays.asList(testId1), null);

        assertEquals(1, recs.size());
        assertEquals(rec, recs.get(0));

        assertEquals(null, recs.get(0).getChildren());
        assertEquals(null, recs.get(0).getChildIds());
        assertEquals(null, recs.get(0).getParents());
        assertEquals(null, recs.get(0).getParentIds());

        assertBaseCount(testBase1, 1);

        assertTrue(storage.getModificationTime(testBase1) >= testStartTime);
        assertTrue(storage.getModificationTime(null) >= testStartTime);
    }

    /**
     * Test update multi flush.
     * @throws Exception If error.
     */
    public void testUpdateMultiFlush() throws Exception {
        Record plain = new Record(testId1, testBase1, testContent1);
        Record deleted = new Record(testId1, testBase1, testContent1);
        deleted.setDeleted(true);

        assertNull("The test record should not exist",
                   storage.getRecord(plain.getId(), null));
        storage.flushAll(Arrays.asList(deleted));
        storage.flushAll(Arrays.asList(plain, plain));
        storage.flushAll(Arrays.asList(plain, deleted, plain));
        storage.flushAll(Arrays.asList(plain, deleted, deleted, plain));
    }

    /**
     * Test that record content is indeed updated when calling the flush().
     * @throws Exception  If error.
     */
    public void testContentUpdate() throws Exception {
        Record orig = new Record(testId1, testBase1, testContent1);
        Record upd = new Record(testId1, testBase1, testContent2);

        assertNull("The test record should not exist",
                   storage.getRecord(testId1, null));
        storage.flush(orig);
        Record _orig = storage.getRecord(testId1, null);
        assertEquals(orig, _orig);

        storage.flush(upd);
        Record _upd = storage.getRecord(testId1, null);
        assertEquals(upd, _upd);

        // This is superfluous, but we are paranoid
        assertTrue(upd.getContentAsUTF8().equals(_upd.getContentAsUTF8()));
        assertFalse(_orig.getContentAsUTF8().equals(_upd.getContentAsUTF8()));
        assertTrue(_upd.getContentAsUTF8().equals(new String(testContent2)));
    }

    /** 
     * Assert that the TRY_UPDATE meta flag works - ie. that we don't update
     * records that are already up to date
     * @throws Exception If error. 
     */
    public void testTryUpdate() throws Exception {
        Record orig = new Record(testId1, testBase1, testContent1);
        storage.flush(orig);
        long mtime = storage.getRecord(testId1, null).getModificationTime();

        Thread.sleep(50); // Make sure system time is updated

        QueryOptions options = new QueryOptions();
        options.meta("TRY_UPDATE", "true");
        storage.flush(orig, options);
        long newMtime = storage.getRecord(testId1, null).getModificationTime();

        assertEquals(mtime, newMtime); // Record already up to date

        // Make sure that we indeed update it without the TRY_UPDATE
        storage.flush(orig);
        newMtime = storage.getRecord(testId1, null).getModificationTime();
        assertTrue(newMtime > mtime);
    }

    /**
     * Test that record content is indeed updated when calling the flushAll().
     * @throws Exception If error.
     **/
    public void testBatchedContentUpdate() throws Exception {
        Record orig = new Record(testId1, testBase1, testContent1);
        Record upd = new Record(testId1, testBase1, testContent2);

        assertNull("The test record should not exist",
                   storage.getRecord(testId1, null));
        storage.flushAll(Arrays.asList(orig));
        Record _orig = storage.getRecord(testId1, null);
        assertEquals(orig, _orig);

        storage.flushAll(Arrays.asList(upd));
        Record _upd = storage.getRecord(testId1, null);
        assertEquals(upd, _upd);

        // This is superfluous, but we are paranoid
        assertTrue(upd.getContentAsUTF8().equals(_upd.getContentAsUTF8()));
        assertFalse(_orig.getContentAsUTF8().equals(_upd.getContentAsUTF8()));
        assertTrue(_upd.getContentAsUTF8().equals(new String(testContent2)));
    }

    public void testClearOne() throws Exception {
        testAddOne();
        storage.clearBase(testBase1);
        assertBaseEmpty(testBase1, 1);
    }

    // Flush single records repeatedly with flush()
    public void testClearAndUpdate() throws Exception {
        int NUM_RUNS = 1; // 30;
        int NUM_RECS = 1027;

        for (int i = 0; i < NUM_RUNS; i++) {
            // Put NUM_RECS records in storage in testBase1
            for (int j = 0; j < NUM_RECS; j++) {
                storage.flush(new Record("test" + j, testBase1, testContent1));
            }

            // Test getting records by mtime
            Iterator<Record> iter = new StorageIterator(
                  storage, storage.getRecordsModifiedAfter(0, testBase1, null));
            for (int j = 0; j < NUM_RECS; j++) {
                assertTrue("Base " + testBase1 + " must hold at least " + NUM_RECS + " records. Found " + j,
                           iter.hasNext());
                Record rec = iter.next();
                assertEquals("test" + j, rec.getId());
                assertEquals(testBase1, rec.getBase());
                assertEquals(new String(testContent1), rec.getContentAsUTF8());
                assertFalse(rec.isDeleted());
            }
            assertFalse("Base " + testBase1 + " must contain at max " + NUM_RECS + " records", iter.hasNext());

            // Clear the test base. Rinse, repeat
            storage.clearBase(testBase1);
            assertBaseEmpty(testBase1, NUM_RECS);
        }
    }

    // Flush batches of records repeatedly with flushAll()
    public void testClearAndUpdateBatch() throws Exception {
        int NUM_RUNS = 3; //30;
        int NUM_RECS = 1000;

        for (int i = 0; i < NUM_RUNS; i++) {
            // Put NUM_RECS records in storage in testBase1
            List<Record> recs = new ArrayList<Record>(NUM_RECS);
            for (int j = 0; j < NUM_RECS; j++) {
                recs.add(new Record("test" + j, testBase1, testContent1));
            }
            storage.flushAll(recs);

            // Test getting records by mtime
            Iterator<Record> iter = new StorageIterator(storage, storage.getRecordsModifiedAfter(0, testBase1, null));
            for (int j = 0; j < NUM_RECS; j++) {
                assertTrue("Base " + testBase1 + " must hold at least " + NUM_RECS + " records. Found " + j,
                           iter.hasNext());
                Record rec = iter.next();
                assertEquals("test" + j, rec.getId());
                assertEquals(testBase1, rec.getBase());
                assertEquals(new String(testContent1), rec.getContentAsUTF8());
                assertFalse(rec.isDeleted());
            }
            assertFalse("Base " + testBase1 +" must contain at max " + NUM_RECS + " records", iter.hasNext());

            // Clear the test base. Rinse, repeat
            storage.clearBase(testBase1);
            assertBaseEmpty(testBase1, NUM_RECS);
        }
    }

    public void testAddOneWithOneChildId() throws Exception {
        Record rec = new Record(testId1, testBase1, testContent1);
        rec.setChildIds(Arrays.asList(testId2));
        storage.flush(rec);

        List<Record> recs = storage.getRecords(Arrays.asList(testId1), null);

        assertEquals(1, recs.size());
        assertEquals(rec, recs.get(0));

        assertEquals(null, recs.get(0).getChildren());
        assertEquals(Arrays.asList(testId2), recs.get(0).getChildIds());
        assertEquals(null, recs.get(0).getParents());
        assertEquals(null, recs.get(0).getParentIds());

        assertBaseCount(testBase1, 1);
    }

    public void testAddOneWithTwoChildIds() throws Exception {
        Record rec = new Record(testId1, testBase1, testContent1);
        rec.setChildIds(Arrays.asList(testId2, testId3));
        storage.flush(rec);

        List<Record> recs = storage.getRecords(Arrays.asList(testId1), null);

        assertEquals(1, recs.size());
        assertEquals(rec, recs.get(0));

        assertEquals(null, recs.get(0).getChildren());
        assertEquals(Arrays.asList(testId2, testId3),
                     recs.get(0).getChildIds());
        assertEquals(null, recs.get(0).getParents());
        assertEquals(null, recs.get(0).getParentIds());

        assertBaseCount(testBase1, 1);
    }

    public void testAddOneWithOneParentId() throws Exception {
        Record rec = new Record(testId1, testBase1, testContent1);
        rec.setParentIds(Arrays.asList(testId2));
        storage.flush(rec);

        List<Record> recs = storage.getRecords(Arrays.asList(testId1), null);

        assertEquals(1, recs.size());
        assertNotNull(recs.get(0).getParentIds());
        assertEquals(rec, recs.get(0));

        assertEquals(null, recs.get(0).getChildren());
        assertEquals(null, recs.get(0).getChildIds());
        assertEquals(null, recs.get(0).getParents());
        assertEquals(Arrays.asList(testId2), recs.get(0).getParentIds());

        assertBaseCount(testBase1, 1);
    }

    public void testAddOneWithTwoParentIds() throws Exception {
        Record rec = new Record(testId1, testBase1, testContent1);
        rec.setParentIds(Arrays.asList(testId2, testId3));
        storage.flush(rec);

        List<Record> recs = storage.getRecords(Arrays.asList(testId1), null);

        assertEquals(1, recs.size());
        assertEquals(rec, recs.get(0));

        assertEquals(null, recs.get(0).getChildren());
        assertEquals(null, recs.get(0).getChildIds());
        assertEquals(null, recs.get(0).getParents());
        assertEquals(Arrays.asList(testId2, testId3),
                     recs.get(0).getParentIds());

        assertBaseCount(testBase1, 1);
    }

    /**
     * Test AddTwo.
     * @throws Exception If error.
     */
    public final void testAddTwo() throws Exception {
        Record rec1 = new Record(testId1, testBase1, testContent1);
        Record rec2 = new Record(testId2, testBase1, testContent1);
        storage.flushAll(Arrays.asList(rec1, rec2));

        List<Record> recs = storage.getRecords(Arrays.asList(testId1, testId2), null);

        assertEquals(2, recs.size());

        assertEquals(rec1, recs.get(0));
        assertEquals(null, recs.get(0).getChildIds());
        assertEquals(null, recs.get(0).getChildren());
        assertEquals(null, recs.get(0).getParentIds());
        assertEquals(null, recs.get(0).getParents());

        assertEquals(rec2, recs.get(1));
        assertEquals(null, recs.get(1).getChildIds());
        assertEquals(null, recs.get(1).getChildren());
        assertEquals(null, recs.get(1).getParentIds());
        assertEquals(null, recs.get(1).getParents());

        assertBaseCount(testBase1, 2);

        assertTrue(storage.getModificationTime(testBase1) >= testStartTime);
        assertTrue(storage.getModificationTime(null) >= testStartTime);
    }

    /**
     * Test add two with time stamp sort.
     * @throws Exception If error.
     */
    public final void testAddTwoWithTimestampSort() throws Exception {
        Record rec1 = new Record(testId1, testBase1, testContent1);
        Record rec2 = new Record(testId2, testBase1, testContent1);

        // Commit the records with id sorting reversed with 100ms delay
        storage.flush(rec2);
        Thread.sleep(100);
        storage.flush(rec1);

        long iterKey = storage.getRecordsModifiedAfter(0, testBase1, null);
        Iterator<Record> iter = new StorageIterator(storage, iterKey);

        Record r = iter.next();
        assertEquals(rec2, r); // The first record flushed should be first

        r = iter.next();
        assertEquals(rec1, r); // The last record flushed should be last

        assertFalse("Storage should contain exactly two records",
                    iter.hasNext());
    }

    public void testClearTwo() throws Exception {
        testAddTwo();
        storage.clearBase(testBase1);
        assertBaseEmpty(testBase1);
    }

    public void testExpandShallowRecord() throws Exception {
        testAddOne();

        QueryOptions options = new QueryOptions(null, null, 1, 0);
        List<Record> recs = storage.getRecords(Arrays.asList(testId1), options);
        assertEquals(1, recs.size());

        Record rec = recs.get(0);
        assertEquals(rec.getContentAsUTF8(), rec.getContentAsUTF8());
        assertEquals(rec.getId(), rec.getId());
    }

    public void testFullExpandShallowRecord() throws Exception {
        testAddOne();

        QueryOptions options = new QueryOptions(null, null, -1, 0);
        List<Record> recs = storage.getRecords(Arrays.asList(testId1), options);
        assertEquals(1, recs.size());

        Record rec = recs.get(0);
        assertEquals(rec.getContentAsUTF8(), rec.getContentAsUTF8());
        assertEquals(rec.getId(), rec.getId());
    }

    public void testAddLinkedRecords () throws Exception {
        Record recP = new Record(testId1, testBase1, testContent1);
        Record recC1 = new Record(testId2, testBase1, testContent1);
        Record recC2 = new Record(testId3, testBase1, testContent1);

        recP.setChildIds(Arrays.asList(recC1.getId(), recC2.getId()));

        recC1.setChildIds(Arrays.asList(recC2.getId()));
        recC1.setParentIds(Arrays.asList(recP.getId()));

        recC2.setParentIds(Arrays.asList(recC1.getId()));

        storage.flushAll (Arrays.asList(recP, recC1, recC2));

        /* Fetch without expansion, we test that elewhere */
        List<Record> recs = storage.getRecords(Arrays.asList(testId1, testId2), null);

        assertEquals(2, recs.size());

        log.info("ORIG:\n" + recP.toString(true));
        log.info("GOT :\n" + recs.get(0).toString(true));

        /* We can't compare the records directly because recP has the child
         * records nested, while the retrieved records only has the ids */
        assertEquals(recP.getId(), recs.get(0).getId());
        assertEquals(recP.getBase(), recs.get(0).getBase());
        assertEquals(recP.getContentAsUTF8(), recs.get(0).getContentAsUTF8());

        /* We should have the ids of the children, but they should not be
         * expanded */
        assertEquals(recP.getChildIds(), recs.get(0).getChildIds());
        assertEquals(null, recs.get(0).getChildren());


        assertEquals(recC1.getContentAsUTF8(), recs.get(1).getContentAsUTF8());
        assertEquals(recC1.getId(), recs.get(1).getId());
        assertEquals(recC1.getBase(), recs.get(1).getBase());
        assertEquals(recC1.getParentIds(), recs.get(1).getParentIds());

        assertBaseCount(testBase1, 3);
    }

    public void testExpandLinkedRecord () throws Exception {
        testAddLinkedRecords();

        /* Fetch records expanding immediate children only */
        QueryOptions options = new QueryOptions(null, null, 1, 0);
        List<Record> recs = storage.getRecords(Arrays.asList(testId1, testId2), options);

        assertEquals(2, recs.size());

        /* Check that the first record holds a child relation to the next */
        assertEquals(2, recs.get(0).getChildren().size());
        assertEquals(testId2, recs.get(0).getChildren().get(0).getId());

        /* testId3 is a child of testId2 and should be expanded */
        assertEquals(1, recs.get(1).getChildren().size());
        assertEquals(1, recs.get(1).getChildIds().size());
        assertEquals(testId3, recs.get(1).getChildIds().get(0));
        assertEquals(testId3, recs.get(1).getChildren().get(0).getId());

    }

    public void testRecursiveExpandLinkedRecord () throws Exception {
        testAddLinkedRecords();

        /* Fetch records expanding immediate children only */
        QueryOptions options = new QueryOptions(null, null, -1, 0);
        List<Record> recs = storage.getRecords(Arrays.asList(testId1, testId2, testId3), options);

        assertEquals(3, recs.size());

        /* Check that the first record holds child relations to recC1, and recC2 */
        assertEquals(2, recs.get(0).getChildren().size());
        assertEquals(recs.get(1), recs.get(0).getChildren().get(0));
        assertEquals(recs.get(2), recs.get(0).getChildren().get(1));        

        /* Check that recC1 has child recC2 */
        assertEquals(recs.get(1).getChildren(), Arrays.asList(recs.get(2)));
    }

    /**
     * Test that ingesting one record also ingests any child records on it
     * @throws Exception if error.
     */
    public void testAddNestedRecords () throws Exception {
        Record recP = new Record (testId1, testBase1, testContent1);
        Record recC1 = new Record (testId2, testBase1, testContent1);
        Record recC2 = new Record (testId3, testBase1, testContent1);
        Record recCC1 = new Record (testId4, testBase1, testContent1);

        /* We need to explicitly set all relations here to make the assertions
         * work*/

        recP.setChildren(Arrays.asList(recC1, recC2));

        recC1.setParents (Arrays.asList(recP));
        recC1.setChildren(Arrays.asList(recCC1));
        recC1.setIndexable(false);

        recC2.setParents(Arrays.asList(recP));
        recC2.setIndexable(false);

        recCC1.setParents(Arrays.asList(recC1));
        recCC1.setIndexable(false);

        /* The child records should be implicitly flushed as well */
        storage.flushAll (Arrays.asList(recP));

        QueryOptions options = new QueryOptions(null, null, -1, 0);
        List<Record> recs = storage.getRecords(Arrays.asList(testId1, testId2, testId3, testId4), options);

        assertEquals(4, recs.size());

        assertEquals(recP, recs.get(0));
        assertEquals(recC1, recs.get(1));
        assertEquals(recC2, recs.get(2));
        assertEquals(recCC1, recs.get(3));

        assertNotNull(recs.get(0).getChildren());
        assertNotNull(recs.get(0).getChildIds());
        assertEquals(2, recs.get(0).getChildren().size());
        assertEquals(2, recs.get(0).getChildIds().size());

        assertEquals(recs.get(0).getChildren().get(0), recC1);
        assertEquals(recs.get(0).getChildren().get(1), recC2);

        assertNotNull(recs.get(1).getChildren());
        assertNotNull(recs.get(1).getChildIds());
        assertEquals(1, recs.get(1).getChildren().size());
        assertEquals(1, recs.get(1).getChildIds().size());
        assertEquals(recCC1, recs.get(1).getChildren().get(0));

        assertNull(recs.get(2).getChildren());
        assertNull(recs.get(2).getChildIds());

        assertNull(recs.get(3).getChildren());
        assertNull(recs.get(3).getChildIds());

        assertBaseCount(testBase1, 4);
    }

    /*
     * Puts records in Storage that has cyclic child relations then extracts
     * one of the records with a childRecursionDepth > 0.
     * 
     * This unit-test did indeed result in an endless loop. Method uncommented
     */
    
    public void xxxtestEndlessRecursion () throws Exception {
        Record recP = new Record (testId1, testBase1, testContent1);
        Record recC1 = new Record (testId2, testBase1, testContent1);

        recP.setChildIds(Arrays.asList(recC1.getId()));
        recP.setParentIds(Arrays.asList(recC1.getId()));
        recC1.setChildIds(Arrays.asList(recP.getId()));
        recC1.setParentIds(Arrays.asList(recP.getId()));

        storage.flushAll(Arrays.asList(recP, recC1));

        QueryOptions options = new QueryOptions(null, null, 5, 5);
        List<Record> recs = storage.getRecords(Arrays.asList(testId1), options);
        assertEquals(1, recs.size());

        Record extracted = recs.get(0);
        assertNotNull("The extracted record should have childs defined",
                      extracted.getChildren());
        assertEquals("The extracted record should have a child",
                     1, extracted.getChildren().size());
        assertEquals("The extracted records child should have the correct ID",
                     testId2, extracted.getChildren().get(0).getId());
    }

    /**
     * Test that ctime is preserved but mtime is updated when flushing the same
     * record two times.
     *
     * @throws Exception if error.
     */
    public void testTimestampUpdates () throws Exception {
        Record r1 = new Record(testId1, testBase1, testContent1);

        long beforeFlushTime = System.currentTimeMillis();
        storage.flush(r1);
        long afterFlushTime = System.currentTimeMillis();

        Record r2 = storage.getRecords(Arrays.asList(testId1), null).get(0);

        assertTrue(beforeFlushTime <= r2.getModificationTime());
        assertTrue(beforeFlushTime <= r2.getCreationTime());
        assertTrue(afterFlushTime >= r2.getModificationTime());
        assertTrue(afterFlushTime >= r2.getCreationTime());

        assertEquals(r1, r2);
        long ctime = r2.getCreationTime();
        long mtime = r2.getModificationTime();

        assertEquals("For new records mtime == ctime", ctime, mtime);

        storage.flush(r1);
        r2 = storage.getRecords(Arrays.asList(testId1), null).get(0);

        assertEquals(ctime, r2.getCreationTime());
        assertTrue(mtime <= r2.getModificationTime());
    }

    public void testGetMorerecords() throws Exception {
        int WANTED_RECORDS = Math.max(DatabaseStorage.DEFAULT_PAGE_SIZE * 3 + 5, StorageIterator.MAX_QUEUE_SIZE + 5);

        for (int i = 0; i < WANTED_RECORDS; i++) {
            storage.flush(new Record("Foo" + i, testBase1, testContent1));
        }

        StorageIterator records = new StorageIterator(
                storage, storage.getRecordsModifiedAfter(0, testBase1, null));
        int count = 0;
        while (records.hasNext()) {
            records.next();
            count++;
        }
        assertEquals("The number of received Records should match (and be "
                     + WANTED_RECORDS + ")", WANTED_RECORDS, count);
    }

    public void testGetModifiedAfter() throws Exception {
        Record r1 = new Record(testId1, testBase1, testContent1);
        storage.flush(r1);
        Record r2 = storage.getRecords(Arrays.asList(testId1), null).get(0);
        assertEquals("The timestamp for the retireved record should match the ingested", r1, r2);
        long mtime1 = r1.getModificationTime();

        
        Thread.sleep(100);// So records r1 and r2 will not have same modificationtime
        
        storage.flush(new Record(testId2, testBase2, testContent2));
        long mtime2 = storage.getRecords(Arrays.asList(testId2), null).get(0).getModificationTime();
        assertTrue("Record 1 mtime should be before record 2 mtime",
                   mtime1 < mtime2);

        log.debug("Received modification timestamps: " + mtime1 + " and "
                  + mtime2);
        StorageIterator records = new StorageIterator(
                storage, storage.getRecordsModifiedAfter(mtime1, testBase1, null));
        assertTrue("The iterator should contain a record", records.hasNext());
        Record record = records.next();
        log.debug("Got record from getModifiedAfter(" + mtime1 + ", "
                  + testBase1 + ") with id " + record.getId() + ", mtime "
                  + record.getModificationTime() + ". Time diff: "
                  + (record.getModificationTime() - mtime1));
        if (records.hasNext()) {
        	fail("Record is singular - no more records should be returned. Got record " + records.next());
        }
    }

    /*
     * Assert that touching a child updates its parents recursively upwards
     */
    public void testRecursiveParentUpdates () throws Exception {
        testAddNestedRecords();

        Record recCC1 = storage.getRecord(testId4, null);
        Record recP = storage.getRecord(testId1, null);

        long mtimeCC1 = recCC1.getModificationTime();
        long ctimeCC1 = recCC1.getCreationTime();
        long mtimeP = recP.getModificationTime();
        long ctimeP = recP.getCreationTime();

        /* Touch child recCC1 of recP (nested 2 levels) */
        storage.flush(recCC1);

        recCC1 = storage.getRecord(testId4, null);
        recP = storage.getRecord(testId1, null);

        /* Sanity check that recCC1 has been updated */
        assertTrue(mtimeCC1 < recCC1.getModificationTime());
        assertTrue(ctimeCC1 == recCC1.getCreationTime());

        /* Assert that the parent has been updated as well */
        assertTrue(mtimeP < recP.getModificationTime());
        assertTrue(ctimeP == recP.getCreationTime());
    }

    public void testRecordMetaTags () throws Exception {
        Record r1 = new Record(testId1, testBase1, testContent1);
        r1.addMeta("foo", "bar");
        r1.addMeta("bork", "boo");

        storage.flush(r1);
        Record r2 = storage.getRecords(Arrays.asList(testId1), null).get(0);

        assertEquals(2, r2.getMeta().size());
        assertEquals(r1.getMeta("foo"), r2.getMeta("foo"));
        assertEquals(r1.getMeta("bork"), r2.getMeta("bork"));
        assertEquals(r1, r2);

        assertBaseCount(testBase1, 1);
    }

    public void testFlushAllIdCollisions() throws Exception {
        List<Record> recs = new ArrayList<Record>();

        recs.add(new Record(testId1, testBase1, testContent1));
        recs.add(new Record(testId1, testBase2, testContent1));

        storage.flushAll(recs);

        assertBaseCount(testBase1, 0);
        assertBaseCount(testBase2, 1);
    }

    /*public void testRawSpeed() throws Exception {
        int RUNS = 1000;
        int JOB_SIZE = 100;
        Profiler profiler = new Profiler(RUNS * JOB_SIZE, JOB_SIZE * 3);
        for (int run = 0 ; run < RUNS ; run++) {
            List<Record> recs = new ArrayList<Record>(JOB_SIZE);
            for (int rec = 0 ; rec < JOB_SIZE ; rec++) {
                recs.add(new Record(
                        Integer.toString(run * JOB_SIZE + rec), "dummy",
                        testContent1));
                profiler.beat();
            }
            storage.flushAll(recs);
            log.debug("Flushed " + (run+1) * JOB_SIZE + " records at "
                      + profiler.getBps(true) + " records/sec");
        }
    }

    public void testFlushAllWithNestedChildren() throws Exception {
        List<Record> recs = new ArrayList<Record>();

        recs.add(new Record (testId1, testBase1, testContent1));
        recs.add(new Record (testId2, testBase1, testContent1));
        recs.get(1).setChildren(Arrays.asList(new Record(testId3, testBase1,
                                                         testContent1)));

        storage.flushAll(recs);

        assertBaseCount(testBase1, 3);
    }

    /*
     * Flush several batches of parent/children pairs. This, among other things,
     * will test for connection leaks.
     */
    public void testManyParentChild() throws Exception {
        final int batchCount = 20;
        final int batchSize = 30;
        List<List<Record>> batches = new ArrayList<List<Record>>(batchCount);
        List<String> parentIds = new ArrayList<String>(batchSize);

        for (int j = 0; j < batchCount; j++) {
            List<Record> batch = new ArrayList<Record>(batchSize);
            batches.add(batch);
            for (int i = 0; i < batchSize; i++) {
                batch.add(new Record("parent_" + j + "_" + i, testBase1, testContent1));
                parentIds.add("parent_" + j + "_" + i);
                batch.get(i).setChildren(Arrays.asList(new Record("child_" + j + "_" + i, testBase1, testContent1)));
            }
        }

        for (List<Record> batche : batches) {
            storage.flushAll(batche);
        }

        assertBaseCount(testBase1, batchSize * batchCount * 2);

        List<Record> result = storage.getRecords(parentIds, new QueryOptions(null, null, -1 , -1));
        assertEquals(batchCount * batchSize, result.size());
        for (Record aResult : result) {
            assertEquals(1, aResult.getChildren().size());
            assertEquals("child_", aResult.getChildren().get(0).getId().substring(0, 6));
        }
    }

    public void testTransaction() throws Exception {
        Record plain = new Record(testId1, testBase1, testContent1);
        Record deleted = new Record(testId1, testBase1, testContent1);
        deleted.setDeleted(true);

        String before = Logs.expand(Arrays.asList(listStorageFiles()), 100);
        storage.flushAll(Arrays.asList(plain, deleted, deleted, plain));
        String afterFlush = Logs.expand(Arrays.asList(listStorageFiles()), 100);
        storage.close();
        String afterClose = Logs.expand(Arrays.asList(listStorageFiles()), 100);

        log.info("Database location: "
                           + new File(lastStorageLocation).getAbsolutePath());
        log.info("Before:     " + before);
        log.info("AfterFlush: " + afterFlush);
        assertEquals(before, afterFlush);
        log.info("AfterClose: " + afterClose);
        storage = null;
    }

    private File[] listStorageFiles() throws IOException {
        return new File(lastStorageLocation).listFiles();
    }

    // Copied from the Service API
    public static final String CONF_SERVICE_ID = "summa.control.service.id";
    //public static final String CONF_SERVICE_BASEPATH =
    //                                         "summa.control.service.basepath";
    public static final String CONF_SERVICE_PORT = "summa.control.service.port";
    public static final String CONF_REGISTRY_PORT =
                                          "summa.control.service.registry.port";


    /*public static final String STORAGE_ADDRESS =
            "//localhost:28000/summa-storage";*/
    public static Configuration getStorageConfiguration() {
        Configuration conf = Configuration.newMemoryBased();
        conf.set(DatabaseStorage.CONF_LOCATION,
                 getStorageLocation().toString());
        conf.set(DatabaseStorage.CONF_FORCENEW, true);
        conf.set(CONF_SERVICE_PORT, 27003);
        conf.set(CONF_REGISTRY_PORT, 27000);  // Why is this not done?
        conf.set(CONF_SERVICE_ID, "TestStorage");
        System.setProperty(CONF_SERVICE_ID, "TestStorage");
        return conf;
    }
    public static File getStorageLocation() {
        return new File("target/test_result/storage" + storageCounter++);
    }


    public void testSmallMonkey() throws Exception {
        Configuration storageConf = getStorageConfiguration();
        Storage storage = StorageFactory.createStorage(storageConf);
        StorageMonkeyHelper monkey = new StorageMonkeyHelper(0, 1000000, 0.01, 0.02, null, null, 0, 5, 0, 50);
        monkey.monkey(10, 5, 2, 10, 2, 1, 100);
        storage.close();
    }

    public void testCharMonkey() throws Exception {
        Configuration storageConf = getStorageConfiguration();
        Storage storage = StorageFactory.createStorage(storageConf);

        StringWriter chars = new StringWriter(65535);
        for (char c = 0 ; c < 65535 ; c++) {
            chars.append(c);
        }
        StorageMonkeyHelper monkey = new StorageMonkeyHelper(0, 1000000, 0.01, 0.02, null, null, 0, 5, 0, 50);
        monkey.monkey(10, 5, 2, 10, 2, 1, 100);
        storage.close();
    }

    /*public void testMediumMonkey() throws Exception {
        Configuration storageConf = getStorageConfiguration();
        Storage storage = StorageFactory.createStorage(storageConf);
        StorageMonkeyHelper monkey = new StorageMonkeyHelper(
                0, 1000000, 0.01, 0.02, null, null, 0, 5, 0, 50);
        monkey.monkey(1000, 200, 100, 1000, 5, 1, 100);
        storage.close();
    }*/

    /*public void testUpdateMonkey() throws Exception {
        Configuration storageConf = getStorageConfiguration();
        Storage storage = StorageFactory.createStorage(storageConf);
        StorageMonkeyHelper monkey = new StorageMonkeyHelper(
                0, 1000000, 0.01, 0.02, null, null, 0, 5, 0, 50);
        monkey.monkey(1000, 2000, 100, 1000, 5, 1, 100);
        storage.close();
    }*/

    public void disabledtestLargeMonkey() throws Exception {
        Configuration storageConf = getStorageConfiguration();
        Storage storage = StorageFactory.createStorage(storageConf);
        StorageMonkeyHelper monkey = new StorageMonkeyHelper(
                0, 1000000, 0.01, 0.02, null, null, 0, 5, 0, 50);
        monkey.monkey(10000, 2000, 1000, 1000, 5, 1, 100);
        storage.close();
    }

    public void testPauseResume() throws Exception {
        final int sleepTime = 2 /*10*/ * 1000; 
        Configuration storageConf = getStorageConfiguration();
        Storage storage = StorageFactory.createStorage(storageConf);
        StorageMonkeyHelper monkey = new StorageMonkeyHelper(50, 2000, 0.01, 0.02, null, null, 0, 5, 0, 50);
        List<StorageMonkeyHelper.Job> primaryJobs = monkey.createJobs(10000, 0, 0, 10000, 100, 100);
        log.info("Handling primary jobs");
        monkey.doJobs(primaryJobs, 1);
        log.info("Sleeping " + sleepTime + " ms");
        Thread.sleep(sleepTime);
        log.info("Handling secondary jobs");
        List<StorageMonkeyHelper.Job> secondaryJobs = monkey.createJobs(1000, 0, 0, 1000, 100, 100);
        monkey.doJobs(secondaryJobs, 1);
        log.info("Finished");
        storage.close();
    }

    /*
    Ingests 5 * 1M tiny records

          INFO  [main] [2009-08-28 14:34:15,728] [dk.statsbiblioteket.summa.storage.api.StorageTest] Starting run 1
DEBUG [main] [2009-08-28 14:34:25,559] [dk.statsbiblioteket.summa.storage.api.StorageTest] Did job 1/100 of size 10000 for run 1/5 at 0 records/second. ETA for current job: 2009-08-28 14:34:25
DEBUG [main] [2009-08-28 14:34:30,993] [dk.statsbiblioteket.summa.storage.api.StorageTest] Did job 2/100 of size 10000 for run 1/5 at 1840 records/second. ETA for current job: 2009-08-28 14:43:23
DEBUG [main] [2009-08-28 14:34:35,920] [dk.statsbiblioteket.summa.storage.api.StorageTest] Did job 3/100 of size 10000 for run 1/5 at 1930 records/second. ETA for current job: 2009-08-28 14:42:58
DEBUG [main] [2009-08-28 14:34:40,956] [dk.statsbiblioteket.summa.storage.api.StorageTest] Did job 4/100 of size 10000 for run 1/5 at 2007 records/second. ETA for current job: 2009-08-28 14:42:39
DEBUG [main] [2009-08-28 14:34:46,501] [dk.statsbiblioteket.summa.storage.api.StorageTest] Did job 5/100 of size 10000 for run 1/5 at 1890 records/second. ETA for current job: 2009-08-28 14:43:09
DEBUG [main] [2009-08-28 14:34:54,543] [dk.statsbiblioteket.summa.storage.api.StorageTest] Did job 6/100 of size 10000 for run 1/5 at 1472 records/second. ETA for current job: 2009-08-28 14:45:33
DEBUG [main] [2009-08-28 14:35:03,628] [dk.statsbiblioteket.summa.storage.api.StorageTest] Did job 7/100 of size 10000 for run 1/5 at 1167 records/second. ETA for current job: 2009-08-28 14:48:20
DEBUG [main] [2009-08-28 14:35:12,150] [dk.statsbiblioteket.summa.storage.api.StorageTest] Did job 8/100 of size 10000 for run 1/5 at 1135 records/second. ETA for current job: 2009-08-28 14:48:42
DEBUG [main] [2009-08-28 14:35:21,984] [dk.statsbiblioteket.summa.storage.api.StorageTest] Did job 9/100 of size 10000 for run 1/5 at 1089 records/second. ETA for current job: 2009-08-28 14:49:17
DEBUG [main] [2009-08-28 14:35:31,973] [dk.statsbiblioteket.summa.storage.api.StorageTest] Did job 10/100 of size 10000 for run 1/5 at 1008 records/second. ETA for current job: 2009-08-28 14:50:23
DEBUG [main] [2009-08-28 14:35:47,928] [dk.statsbiblioteket.summa.storage.api.StorageTest] Did job 11/100 of size 10000 for run 1/5 at 770 records/second. ETA for current job: 2009-08-28 14:55:02
DEBUG [main] [2009-08-28 14:35:58,681] [dk.statsbiblioteket.summa.storage.api.StorageTest] Did job 12/100 of size 10000 for run 1/5 at 748 records/second. ETA for current job: 2009-08-28 14:55:33

     */
    /*public void testMassiveTinyFloodNoReopen() throws Exception {
        testMassiveTinyFlood(false, 0);
    }

    public void testMassiveTinyFloodReopen() throws Exception {
        testMassiveTinyFlood(true, 0);
    }

    public void testMassiveTinyFloodReopenPause() throws Exception {
        testMassiveTinyFlood(true, 10 * 1000);
    }

    public void testMassiveTinyFlood(boolean reopen, int delay)
                                                              throws Exception {
        int MIN_CONTENT_SIZE = 10;
        int MAX_CONTENT_SIZE = 200;
        int META_MIN_LENGTH = 1;
        int META_MAX_LENGTH = 10;
        int META_MIN_ENTRIES = 1;
        int META_MAX_ENTRIES = 3;

        int RUNS = 5;
        int JOBS_PER_RUN = 100;

        int JOB_NEW = 9900;
        int JOB_UPDATE = 50;
        int JOB_DELETE = 50;
        int jobSize = JOB_NEW + JOB_UPDATE + JOB_DELETE;

        int FLUSH_SIZE = 100;
        double PARENT_CHANCE = 0.01;
        double CHILD_CHANCE = 0.02;
        int JOBS_PER_REOPEN = 10;

        Configuration storageConf = getStorageConfiguration();
        Storage storage = StorageFactory.createStorage(storageConf);

        // For later re-opens
        storageConf.set(DatabaseStorage.CONF_FORCENEW, false);
        storageConf.set(DatabaseStorage.CONF_CREATENEW, false);

        storage.close();
        storage = StorageFactory.createStorage(storageConf);
        //if (reopen) {
        //    storage.close();
        //}

        StorageMonkeyHelper monkey = new StorageMonkeyHelper(
                MIN_CONTENT_SIZE, MAX_CONTENT_SIZE, PARENT_CHANCE, CHILD_CHANCE,
                null, null, META_MIN_ENTRIES, META_MAX_ENTRIES,
                META_MIN_LENGTH, META_MAX_LENGTH);
        monkey.setCheckForExistingOnDelete(false);

        for (int run = 0 ; run < RUNS ; run++) {
            log.info("Starting run " + (run + 1));
            Profiler profiler = new Profiler(JOBS_PER_RUN);
            profiler.setBpsSpan(3);
            for (int job = 0 ; job < JOBS_PER_RUN ; job++) {
                if (reopen && job % JOBS_PER_REOPEN == 1) {
                    profiler.pause();
                    long beginTime = System.currentTimeMillis();
                    if (delay > 0) {
                        log.debug(String.format(
                                "Pausing for %dms before reopening Storage",
                                delay));
                        Thread.sleep(delay);
                    }
                    log.trace("Closing existing Storage");
                    storage.close();
                    log.trace("Opening new Storage");
                    storage = StorageFactory.createStorage(storageConf);
                    log.debug(String.format("Storage re-opened in %dms",
                              + (System.currentTimeMillis() - beginTime)));
                    profiler.unpause();
                }

                List<StorageMonkeyHelper.Job> jobs = monkey.createJobs(
                        JOB_NEW, JOB_UPDATE, JOB_DELETE,
                        Integer.MAX_VALUE, FLUSH_SIZE, FLUSH_SIZE);
                monkey.doJobs(jobs, 1);
                profiler.beat();
                log.debug(String.format(
                        "Did job %d/%d of size %d for run %d/%d at %s records/"
                        + "second. ETA for current job: %s",
                        job+1, JOBS_PER_RUN, jobs.get(0).size(),
                        (run+1), RUNS,
                        (int)(profiler.getBps(true) * jobSize),
                        profiler.getETAAsString(true)));
            }
        }
        storage.close();
    }*/
}