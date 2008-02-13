package dk.statsbiblioteket.summa.storage.io;

import dk.statsbiblioteket.summa.common.Record;
import dk.statsbiblioteket.summa.common.configuration.Configuration;
import dk.statsbiblioteket.summa.storage.StorageFactory;
import dk.statsbiblioteket.summa.storage.database.DatabaseControl;
import dk.statsbiblioteket.util.Files;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import java.io.File;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.List;

/**
 * This JUnit-test is written with ControlDerby testing in mind. However, it
 * is kept fairly generic so later evolvement to tests for other implementations
 * shouldn't be hard.
 */
@SuppressWarnings({"DuplicateStringLiteralInspection"})
public class ControlTest extends TestCase {
    public ControlTest(String name) {
        super(name);
    }

    static int testdirCounter = 0;
    Control control;
    File location;
    private Configuration conf;

    public void setUp() throws Exception {
        super.setUp();
        location = new File(System.getProperty("java.io.tmpdir"), "controltest"
                                                            + testdirCounter++);
        conf = Configuration.newMemoryBased();
        conf.set(DatabaseControl.PROP_LOCATION, location.toString());

        if (location.exists()) {
            Files.delete(location);
            if (location.exists()) {
                fail("Failed to remove '" + location + "' on setUp");
            }
        }
        control = StorageFactory.createController(conf);
    }

    public void tearDown() throws Exception {
        super.tearDown();
        control.close();
        // We don't delete the files as the Derby JDBC-driver maintains a lock
    }

    public static Test suite() {
        return new TestSuite(ControlTest.class);
    }

    public void testFlush() throws Exception {
        Record record = new Record("foo", "bar", new byte[0]);
        control.flush(record);

        Record storedRecord = control.getRecord("foo");
        assertEquals("The base for the stored record should be correct", 
                     "bar", storedRecord.getBase());
    }

    public void testErrorHandlingOnFaultyNew() throws Exception {
        Record record = new Record("foo", "bar", new byte[0]);
        control.flush(record);
        try {
            control.flush(record);
            fail("An exception should be thrown when reflushing a record marked"
                 + " as new");
        } catch (RemoteException e) {
            System.out.println("Expected exception thrown: " + e.getMessage());
        }
    }

    public void testModified() throws Exception {
        long time = System.currentTimeMillis();
        Record record = new Record("foo", "bar", false, true, 
                                   new byte[]{(byte)1}, time, time, "boo", null,
                                   Record.ValidationState.notValidated);
        control.flush(record);
        assertEquals("Requesting the new stored record shouldn't change "
                     + "anything", record, control.getRecord("foo"));

        time++;
        List<String> children = new ArrayList<String>(2);
        children.add("ping1");
        children.add("ping2");
        Record modified = new Record("foo", "bar2", false, false,
                                   new byte[]{(byte)2}, time, time, "boo2",
                                   children, Record.ValidationState.invalid);
        modified.setModificationTime(time + 5000);

        assertTrue("The record should be classified as modified",
                   modified.isModified());
        try {
            control.flush(modified);
        } catch (RemoteException e) {
            e.printStackTrace();
            fail("Flushing a modified record should not give an exception");
        }
        Record extracted = control.getRecord("foo");
        System.out.println(extracted);
        String m = modified.toString();
        String e = extracted.toString();
        System.out.println("- " + m + " - ");
        System.out.println("- " + e + " - ");
        assertEquals("The modified base should be reflected",
                     modified, extracted);
    }

    public void testModifiedFail() throws Exception {
        Record record = new Record("foo", "bar", new byte[0]);
        record.setModificationTime(System.currentTimeMillis() + 5000);
        try {
            control.flush(record);
        } catch (RemoteException e) {
            fail("Flushing a non-existing modified record should not give an "
                 + "exception");
        }
    }

    public void testDeleted() throws Exception {
        Record record = new Record("foo", "bar", new byte[0]);
        control.flush(record);
        record.setDeleted(true);
        try {
            control.flush(record);
        } catch (RemoteException e) {
            e.printStackTrace();
            fail("Flushing a deleted record should not give an exception");
        }

        // deleting non-existing record does not necessarily means exception
/*        Record nonExisting = new Record("spax", "ghj", new byte[0]);
        try {
            control.flush(nonExisting);
        } catch (RemoteException e) {
            fail("Flushing a non-existing deleted record should give an"
                 + " exception");
        }
  */
    }
}
