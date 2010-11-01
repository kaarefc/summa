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
package dk.statsbiblioteket.summa.storage.api.filter;

import dk.statsbiblioteket.summa.common.Record;
import dk.statsbiblioteket.summa.common.configuration.Configuration;
import dk.statsbiblioteket.summa.common.filter.Payload;
import dk.statsbiblioteket.summa.storage.api.Storage;
import dk.statsbiblioteket.summa.storage.api.StorageFactory;
import dk.statsbiblioteket.summa.storage.database.DatabaseStorage;
import dk.statsbiblioteket.util.Files;
import dk.statsbiblioteket.util.qa.QAInfo;

import java.io.File;
import java.util.Arrays;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Pattern;

import junit.framework.Assert;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

@SuppressWarnings({"DuplicateStringLiteralInspection"})
@QAInfo(level = QAInfo.Level.NORMAL,
        state = QAInfo.State.IN_DEVELOPMENT,
        author = "te")
public class RecordReaderTest extends TestCase {
    private static Log log = LogFactory.getLog(RecordReaderTest.class);
    private static File db = new File("target/test_result",
            "summatest" + File.separator + "recordreadertest");
    private final int timeout = 1000;

    public RecordReaderTest(String name) {
        super(name);
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();
        if (db.exists()) {
            Files.delete(db);
        }
        db.mkdirs();
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        // Clean up and prepare a fresh directory
        //db.mkdirs();
        //Files.delete(db);
    }

    public static Test suite() {
        return new TestSuite(RecordReaderTest.class);
    }

    public static Storage createStorage() throws Exception {
        Configuration conf = Configuration.newMemoryBased(
                                             DatabaseStorage.CONF_LOCATION,
                                             db.getAbsolutePath());

        return StorageFactory.createStorage(conf);
    }

    public void testTimestampFormatting() throws Exception {
        Calendar t = new GregorianCalendar(2008, 3, 17, 21, 50, 57);
        Assert.assertEquals("The timestamp should be properly formatted",
                     expected,
                     String.format(ProgressTracker.TIMESTAMP_FORMAT, t));
    }

    public void waitForHasNext(RecordReader r, long timeout) throws Exception{
        long start = System.currentTimeMillis();

        while (!r.hasNext()) {
            Thread.sleep(100);
            if (System.currentTimeMillis() - start > timeout) {
                fail("RecordReader did not have any records before timeout");
            }
        }
        log.debug("RecordReader has next");
    }

   /*public void testTimestampExtraction() throws Exception {
        Calendar t = new GregorianCalendar(2008, 3, 17, 21, 50, 57);
        long expectedMS = t.getTimeInMillis();
        String formatted = String.format(RecordReader.TIMESTAMP_FORMAT, t);
        assertEquals("Parsing the formatted timestamp-containing text should "
                     + "match the expected point in time",
                     expectedMS, RecordReader.getTimestamp(new File("foo"), 
                                                           formatted));
    }*/

    private static final String expected =
            "<?xml version=\"1.0\" encoding=\"utf-8\" ?>\n"
            + "<lastRecordTimestamp>\n"
            + "<epoch>1208461857000</epoch>\n"
            + "<iso>20080417-215057</iso>\n"
            + "</lastRecordTimestamp>\n";

    public void testPatternMatching() throws Exception {
        Pattern p = Pattern.compile("([0-9]{4})([0-9]{2})([0-9]{2})-"
                                    + "([0-9]{2})([0-9]{2})([0-9]{2})");
        assertTrue("Pattern should match simple case",
                   p.matcher("20080417-215057").matches());

        //String TAG = "lastRecordTimestamp";
        Pattern pfull;/* = Pattern.compile(".*<" + TAG + ">"
                    + "([0-9]{4})([0-9]{2})([0-9]{2})-"
                    + "([0-9]{2})([0-9]{2})([0-9]{2})"
                    + "</" + TAG + ">", Pattern.DOTALL);*/
        pfull = ProgressTracker.TIMESTAMP_PATTERN;
        assertTrue("Pattern should match extended case with input " + expected,
                   pfull.matcher(expected).matches());
        assertTrue("Pattern should match full case",
                   pfull.matcher(expected).matches());
    }

    public void testOne() throws Exception {
        Storage sto = createStorage();
        RecordReader r = new RecordReader(Configuration.newMemoryBased());
        Record orig = new Record("test1", "base", "Hello".getBytes());
        sto.flush(orig);
        System.exit(1);
        waitForHasNext(r, timeout);

        Payload p = r.next();
        Record rec = p.getRecord();

        assertEquals(orig, rec);
        r.close(true);
        sto.close();
    }

    public void testTwo() throws Exception {
        Storage sto = createStorage();
        RecordReader r = new RecordReader(Configuration.newMemoryBased());

        Record orig1 = new Record("test1", "base", "Hello".getBytes());
        Record orig2 = new Record("test2", "base", "Hello".getBytes());

        sto.flushAll(Arrays.asList(orig1, orig2));
        waitForHasNext(r, timeout);

        Payload p = r.next();
        Record rec = p.getRecord();
        assertEquals(orig1, rec);

        p = r.next();
        rec = p.getRecord();
        assertEquals(orig2, rec);
        r.close(true);
        sto.close();
    }

    public void testThree() throws Exception {
        Storage sto = createStorage();
        RecordReader r = new RecordReader(Configuration.newMemoryBased());

        Record orig1 = new Record("test1", "base", "Hello".getBytes());
        Record orig2 = new Record("test2", "base", "Hello".getBytes());
        Record orig3 = new Record("test3", "base", "Hello".getBytes());

        sto.flushAll(Arrays.asList(orig1, orig2, orig3));
        waitForHasNext(r, timeout);

        Payload p = r.next();
        Record rec = p.getRecord();
        assertEquals(orig1, rec);

        p = r.next();
        rec = p.getRecord();
        assertEquals(orig2, rec);

        p = r.next();
        rec = p.getRecord();
        assertEquals(orig3, rec);
        r.close(true);
        sto.close();
    }

    public void testWatchOne() throws Exception {
        Storage sto = createStorage();
        RecordReader r = new RecordReader(Configuration.newMemoryBased(
                RecordReader.CONF_STAY_ALIVE, true
        ));

        // Launch the probe on an empty storage waiting for records
        RecordProbe probe = new RecordProbe(r, 1);
        Thread thread = new Thread(probe, "RecordProbe");
        thread.start();

        Record orig = new Record("test1", "base", "Hello".getBytes());
        sto.flush(orig);

        // Wait for the probe to return, 10s
        thread.join(10000);
        if (probe.records.isEmpty()) {
            fail("No records appeared on reader before timeout");
        } else if (probe.records.size() != 1) {
            fail("Too many records. Expected 1, but got "
                 + probe.records.size());
        }

        assertEquals(orig, probe.records.get(0));
        r.close(true);
        sto.close();
    }

    public void testWatchThree() throws Exception {
        Storage sto = createStorage();
        RecordReader r = new RecordReader(Configuration.newMemoryBased(
                RecordReader.CONF_STAY_ALIVE, true
        ));

        // Launch the probe on an empty storage waiting for records
        RecordProbe probe = new RecordProbe(r, 3);
        Thread thread = new Thread(probe, "RecordProbe");
        thread.start();

        Record orig1 = new Record("test1", "base", "Hello".getBytes());
        Record orig2 = new Record("test2", "base", "Hello".getBytes());
        Record orig3 = new Record("test3", "base", "Hello".getBytes());
        sto.flushAll(Arrays.asList(orig1, orig2, orig3));

        // Wait for the probe to return, 10s
        thread.join(10000);
        if (probe.records.isEmpty()) {
            fail("No records appeared on reader before timeout");
        } else if (probe.records.size() != 3) {
            fail("Too many records. Expected 3, but got "
                 + probe.records.size());
        }

        assertEquals(orig1, probe.records.get(0));
        assertEquals(orig2, probe.records.get(1));
        assertEquals(orig3, probe.records.get(2));
        r.close(true);
        sto.close();
    }

    static class RecordProbe implements Runnable {
        RecordReader r;
        List<Record> records;
        int recordCount;

        public RecordProbe(RecordReader rr, int recordCount) {
            r = rr;
            records = new LinkedList<Record>();
            this.recordCount = recordCount;
        }

        public void run() {
            for (int i = 1; i <= recordCount; i++) {
                Payload p = r.next();
                records.add(p.getRecord());
            }
        }
    }
}
