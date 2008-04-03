package dk.statsbiblioteket.summa.ingest.stream;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.ByteArrayInputStream;

import dk.statsbiblioteket.summa.common.Record;
import dk.statsbiblioteket.summa.common.configuration.Configuration;
import dk.statsbiblioteket.summa.common.filter.Payload;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * XMLSplitterParser Tester.
 *
 * @author <Authors name>
 * @since <pre>04/02/2008</pre>
 * @version 1.0
 */
@SuppressWarnings({"ALL"})
public class XMLSplitterParserTest extends TestCase {
    private static Log log = LogFactory.getLog(XMLSplitterParser.class);

    private final static String headXML =
    "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
    + "<OAI-PMH xmlns=\"http://www.openarchives.org/OAI/2.0/\""
    + " xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\""
    + " xmlns:foo=\"http://example.com/somename\""
    + " xsi:schemaLocation=\"http://www.openarchives.org/OAI/2.0/"
    + " http://www.openarchives.org/OAI/2.0/OAI-PMH.xsd\">\n"
    + "<responseDate>2008-03-12T16:53:55Z</responseDate>\n"
    + "<request verb=\"ListRecords\" metadataPrefix=\"indexRepresentation\">"
    + "http://localhost:7900/oaiprovider/</request>\n"
    + "<ListRecords>\n";

    private final static String tailXML =
    "<resumptionToken cursor=\"0\">X13620492/1</resumptionToken>\n"
    + "</ListRecords>\n"
    + "</OAI-PMH>";

    private final static String singleXML = headXML
    + "<record xmlns=\"http://www.openarchives.org/OAI/2.0/\">\n"

    + "<header>\n"
    + "<identifier>test:SingleElemmet</identifier>\n"
    + "<datestamp>2008-03-12T14:40:37Z</datestamp>\n"
    + "<single foo=\"bar\"/>\n"
    + "<empty/>\n"
    + "<amp>&amp;</amp>\n"
    + "<intertwine>boo<sub>subbio</sub></intertwine>\n"
    + "<emptywithtag mytag=\"bar\"/>\n"
    + "<!-- Comment -->\n"
    + "<embeddedrecord><record>Hello</record></embeddedrecord>\n"
    + "<cdata><![CDATA[Evil & Live <, \\>,]]></cdata>"
    + "</header>\n"

    + "<metadata>\n"
    + "<d:digitalObjectBundle xmlns:d=\"http://fedora.statsbiblioteket."
    + "dk/datatypes/digitalObjectBundle/\">"
    + "<foxml:digitalObject xmlns:foxml=\"info:fedora/fedora-system:def/foxm"
    + "l#\" xmlns:fedoraxsi=\"http://www.w3.org/2001/XMLSchema-instance\" "
    + "xmlns:audit=\"info:fedora/fedora-system:def/audit#\" "
    + "FEDORA_URI=\"info:fedora/doms:HarkonnenIndustries__Carryall_C4_10012"
    + "_revyItem\" PID=\"test:SingleElemmet\""
    + " fedoraxsi:schemaLocation=\"info:fedora/fedora-system:def/foxml#"
    + " http://www.fedora.info/definitions/1/0/foxml1-0.xsd\">\n"

    + "<foxml:objectProperties>\n"
    + "    <foxml:property"
    + " NAME=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#type\""
    + " VALUE=\"FedoraObject\"/>\n"
    + "</foxml:objectProperties>\n"
    + "</foxml:digitalObject>"
    + "</d:digitalObjectBundle>"
    + "</metadata>\n"

    + "</record>\n"
    + tailXML;

    private final static String multiXML = headXML
    + "<record xmlns=\"http://www.openarchives.org/OAI/2.0/\">\n"

    + "<header>\n"
    + "<identifier>test:FirstElement</identifier>\n"
    + "<datestamp>2008-03-12T14:40:37Z</datestamp>\n"
    + "</header>\n"

    + "<metadata>\n"
    + "<d:digitalObjectBundle xmlns:d=\"http://fedora.statsbiblioteket."
    + "dk/datatypes/digitalObjectBundle/\">"
    + "<foxml:digitalObject xmlns:foxml"
    + "=\"info:fedora/fedora-system:def/foxml#\" "
    + "xmlns:fedoraxsi=\"http://www.w3.org/2001/XMLSchema-instance\" "
    + "xmlns:audit=\"info:fedora/fedora-system:def/audit#\" "
    + "FEDORA_URI=\"info:fedora/doms:HarkonnenIndustries__Carryall_C4_10012"
    + "_revyItem\" PID=\"test:FirstElement\""
    + " fedoraxsi:schemaLocation=\"info:fedora/fedora-system:def/foxml#"
    + " http://www.fedora.info/definitions/1/0/foxml1-0.xsd\">\n"

    + "<foxml:objectProperties>\n"
    + "    <foxml:property"
    + " NAME=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#type\""
    + " VALUE=\"FedoraObject\"/>\n"
    + "</foxml:objectProperties>\n"
    + "</foxml:digitalObject>"
    + "</d:digitalObjectBundle>"
    + "</metadata>\n"

    + "</record>\n"

    + "<record xmlns=\"http://www.openarchives.org/OAI/2.0/\">\n"

    + "<header>\n"
    + "<identifier>test:SecondElement</identifier>\n"
    + "<datestamp>2008-03-12T14:42:37Z</datestamp>\n"
    + "</header>\n"

    + "<metadata>\n"
    + "<d:digitalObjectBundle xmlns:d=\"http://fedora.statsbiblioteket."
    + "dk/datatypes/digitalObjectBundle/\"><foxml:digitalObject xmlns:foxml"
    + "=\"info:fedora/fedora-system:def/foxml#\" "
    + "xmlns:fedoraxsi=\"http://www.w3.org/2001/XMLSchema-instance\" "
    + "xmlns:audit=\"info:fedora/fedora-system:def/audit#\" "
    + "FEDORA_URI=\"info:fedora/doms:HarkonnenIndustries__Carryall_C4_10012"
    + "_revyItem\" PID=\"test:SecondElement\""
    + " fedoraxsi:schemaLocation=\"info:fedora/fedora-system:def/foxml#"
    + " http://www.fedora.info/definitions/1/0/foxml1-0.xsd\">\n"

    + "<foxml:objectProperties>\n"
    + "    <foxml:property"
    + " NAME=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#type\""
    + " VALUE=\"FedoraObject\"/>\n"
    + "</foxml:objectProperties>\n"
    + "</foxml:digitalObject>"
    + "</d:digitalObjectBundle>"
    + "</metadata>\n"

    + "</record>\n"
    + tailXML;

    private static final String noNameXML =
    "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
    + "<outer>\n"
    + "<simple foo=\"bar\">some content</simple>\n"
    + "<empty/>\n"
    + "<emptywhite  />\n"
    + "<emptyspan></emptyspan>\n"
    + "<nearlyempty sometag=\"zoo\"/>\n"
    + "</outer>";


    public XMLSplitterParserTest(String name) {
        super(name);
    }

    public void setUp() throws Exception {
        super.setUp();
    }

    public void tearDown() throws Exception {
        super.tearDown();
    }

    public static Test suite() {
        return new TestSuite(XMLSplitterParserTest.class);
    }

    public void testSAX() throws Exception {
        SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setNamespaceAware(true);
        SAXParser parser = factory.newSAXParser();
//        parser.setProperty();
        assertTrue("The parser should be namespaceaware", 
                   parser.isNamespaceAware());
    }

    private Configuration getBasicConfiguration() {
        Configuration conf = Configuration.newMemoryBased();
        conf.set(XMLSplitterFilter.CONF_BASE, "testbase");
        conf.set(XMLSplitterFilter.CONF_COLLAPSE_PREFIX, "true");
        conf.set(XMLSplitterFilter.CONF_ID_ELEMENT, "identifier");
        conf.set(XMLSplitterFilter.CONF_ID_PREFIX, "myprefix");
        conf.set(XMLSplitterFilter.CONF_PRESERVE_NAMESPACES, "true");
        conf.set(XMLSplitterFilter.CONF_RECORD_ELEMENT, "record");
        conf.set(XMLSplitterFilter.CONF_REQUIRE_VALID, "true");
        return conf;
    }

    public void testSimpleParse() throws Exception {
        Configuration conf = getBasicConfiguration();
        XMLSplitterFilter.Target target = new XMLSplitterFilter.Target(conf);
        XMLSplitterParser parser = new XMLSplitterParser(target);
        ByteArrayInputStream stream =
                new ByteArrayInputStream(singleXML.getBytes("utf-8"));

        parser.openPayload(new Payload(stream));
        assertTrue("parser should have something",
                   parser.hasNext());
        Payload payload = parser.next();
        assertNotNull("Something should be returned by parser.next()",
                      payload);
        assertFalse("After one request, the parser should be empty",
                    parser.hasNext());
        log.debug("Got Record " + payload.getRecord() + " with content\n" +
                  payload.getRecord().getContentAsUTF8());
    }

    public void testMultiParse() throws Exception {
        Configuration conf = getBasicConfiguration();
        XMLSplitterFilter.Target target = new XMLSplitterFilter.Target(conf);
        XMLSplitterParser parser = new XMLSplitterParser(target);
        ByteArrayInputStream stream =
                new ByteArrayInputStream(multiXML.getBytes("utf-8"));

        parser.openPayload(new Payload(stream));
        assertTrue("parser should have something",
                   parser.hasNext());
        Payload payload = parser.next();
        assertNotNull("Something should be returned by parser.next()",
                      payload);
        assertNotNull("Something should be returned by next() second call",
                      parser.next());
        assertFalse("After two requests, the parser should be empty",
                    parser.hasNext());
    }

    // TODO: Check for extracted id and prefix

    public void testNoNamespaceParse() throws Exception {
        Configuration conf = getBasicConfiguration();
        conf.set(XMLSplitterFilter.CONF_RECORD_ELEMENT, "outer");
        conf.set(XMLSplitterFilter.CONF_ID_ELEMENT, "nearlyempty#sometag");
        XMLSplitterFilter.Target target = new XMLSplitterFilter.Target(conf);
        XMLSplitterParser parser = new XMLSplitterParser(target);
        ByteArrayInputStream stream =
                new ByteArrayInputStream(noNameXML.getBytes("utf-8"));

        parser.openPayload(new Payload(stream));
        assertTrue("parser should have something",
                   parser.hasNext());
        Payload payload = parser.next();
        assertNotNull("Something should be returned by parser.next()",
                      payload);
        assertFalse("After one request, the parser should be empty",
                    parser.hasNext());
    }

    // Parseexception
}
