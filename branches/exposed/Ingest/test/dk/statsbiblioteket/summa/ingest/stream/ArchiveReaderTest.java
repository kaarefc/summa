package dk.statsbiblioteket.summa.ingest.stream;

import de.schlichtherle.truezip.file.TArchiveDetector;
import de.schlichtherle.truezip.file.TFile;
import dk.statsbiblioteket.summa.common.configuration.Configuration;
import dk.statsbiblioteket.summa.common.configuration.Resolver;
import dk.statsbiblioteket.summa.common.filter.Payload;
import dk.statsbiblioteket.summa.common.unittest.ExtraAsserts;
import dk.statsbiblioteket.util.Files;
import dk.statsbiblioteket.util.qa.QAInfo;
import junit.framework.Test;
import junit.framework.TestSuite;
import junit.framework.TestCase;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@QAInfo(level = QAInfo.Level.NORMAL,
        state = QAInfo.State.IN_DEVELOPMENT,
        author = "te")
public class ArchiveReaderTest extends TestCase {
    public ArchiveReaderTest(String name) {
        super(name);
    }

    File TMP;
    @Override
    public void setUp() throws Exception {
        super.setUp();
        File t = Resolver.getFile("data/zip");
        assertTrue(
            "The test data source 'data/zip' should exist", t.exists());
        TMP = new File(t.getParent(), "ZIPTMP").getAbsoluteFile();
        if (TMP.exists()) {
            Files.delete(TMP);
        }
        Files.copy(t, TMP, false);
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
//        Files.delete(TMP);
    }

    public static Test suite() {
        return new TestSuite(ArchiveReaderTest.class);
    }

    public void testZIPFilenames() throws Exception {
        testZIPDetection(
            new File(TMP, "zip32.zip").getAbsolutePath());
        // Colon is not supported by TrueZIP 7.0pr1
        //testZIPDetection("data/zip/zip:colon.zip");
        testZIPDetection(
            new File(TMP, "double_stuffed2.zip").getAbsolutePath());
    }

    public void testVerifyTrueZIPCapabilities() throws Exception {
        TArchiveDetector detector = TFile.getDefaultArchiveDetector();
        assertTrue("TrueZIP should support ZIP",
                   detector.toString().contains("zip"));
    }

    public void testZIPDetection(String zipString) throws Exception {
        File ZIP = Resolver.getFile(zipString);

        assertNotNull(
            "Resolver.getFile(" + zipString + ") should not return null", ZIP);
        assertTrue("File '" + ZIP + "' should exist using standard Java API",
                   ZIP.exists());

        System.out.println("Resolver.getFile(" + zipString + ") == " + ZIP);
        System.out.println("Resolver.getFile(" + zipString + ").toURI() == "
                           + ZIP.toURI());

        //TFile file = new TFile(ZIP);
        TFile file = new TFile(Resolver.getFile(zipString));
        try {
            assertTrue("File '" + ZIP + "' should exist", file.exists());
        } catch (NullPointerException e) {
            //noinspection CallToPrintStackTrace
            e.printStackTrace();
            fail("Calling TFile.exists() on '" + file + "' gave NPE");
        }
        assertTrue("File '" + ZIP + "' resolved to '" + file
                   + "' should be an archive",
                   file.isArchive());
    }

    public void testDeep() throws Exception {
        testRecursive(TMP.getAbsolutePath(), null);
    }

    public void testSimple() throws Exception {
        testRecursive(new File(TMP, "subfolder/").getAbsolutePath(),
                      Arrays.asList("kaboom.xml",
                                    "zoo.xml",
                                    "zoo2.xml"));
    }

    public void testDoubleZIP() {
        testRecursive(new File(TMP, "subdouble/").getAbsolutePath(),
                      Arrays.asList("foo.xml",
                                    "double_zipped_foo2.xml",
                                    "kaboom.xml",
                                    "zoo.xml",
                                    "zoo2.xml",
                                    "non_zipped_foo.xml"));
    }

    public void testRename() {
        assertFile(new File(TMP, "subfolder/zoo.xml"));
        testRecursive(new File(TMP, "subfolder").getAbsolutePath(), null);
        assertFile(new File(TMP, "subfolder/zoo.xml.finito"));
    }

    public void testRenameEmbedded() {
        assertFile(new File(TMP, "subdouble/sub2/non_zipped_foo.xml"));
        assertFile(
            new File(TMP, "subdouble/sub2/double_stuffed.zip").getAbsolutePath()
            + "/foo.xml");
        testRecursive(new File(TMP, "subdouble/").getAbsolutePath(), null);
        assertFile(new File(TMP, "subdouble/sub2/non_zipped_foo.xml.finito"));
        assertFile(new File(TMP, "subdouble/sub2/double_stuffed.zip.finito"));
        assertNotFile(
            new File(TMP, "subdouble/sub2/double_stuffed.zip").getAbsolutePath()
            + "/foo.xml.finito");
    }

    private void assertFile(File file) {
        assertTrue("The file '" + file.getAbsolutePath() + "' should exist",
                   new TFile(file).exists());
    }
    private void assertFile(String path) {
        assertTrue("The file '" + path + "' should exist",
                   new TFile(path).exists());
    }
    private void assertNotFile(String path) {
        assertTrue("The file '" + path + "' should not exist",
                   !new TFile(path).exists());
    }

    public void testRecursive(String source, List<String> expected) {
        Configuration conf = Configuration.newMemoryBased();
        conf.set(FileReader.CONF_ROOT_FOLDER, source);
        conf.set(FileReader.CONF_RECURSIVE, true);
        conf.set(FileReader.CONF_FILE_PATTERN, ".*\\.xml");
        conf.set(FileReader.CONF_COMPLETED_POSTFIX, ".finito");
        ArchiveReader reader = new ArchiveReader(conf);

        ArrayList<String> actual = new ArrayList<String>();
        while (reader.hasNext()) {
            Payload payload = reader.next();
            actual.add(new File((String)
                    payload.getData(Payload.ORIGIN)).getName());
            payload.close();
        }
        if (expected == null) {
            for (String a: actual) {
                System.out.println(a);
            }
        } else {
            ExtraAsserts.assertEquals(
                "The resulting files should be as expected", expected, actual);
        }
    }

}
