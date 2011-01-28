/* $Id:$
 *
 * The Summa project.
 * Copyright (C) 2005-2010  The State and University Library
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package dk.statsbiblioteket.summa.support.summon.search;

import dk.statsbiblioteket.summa.common.configuration.Configuration;
import dk.statsbiblioteket.summa.common.util.ConvenientMap;
import dk.statsbiblioteket.summa.facetbrowser.api.FacetResult;
import dk.statsbiblioteket.summa.facetbrowser.api.FacetResultExternal;
import dk.statsbiblioteket.summa.search.api.Request;
import dk.statsbiblioteket.summa.search.api.ResponseCollection;
import dk.statsbiblioteket.summa.search.api.document.DocumentKeys;
import dk.statsbiblioteket.summa.search.api.document.DocumentResponse;
import dk.statsbiblioteket.util.qa.QAInfo;
import dk.statsbiblioteket.util.xml.DOM;
import dk.statsbiblioteket.util.xml.XMLUtil;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.logging.Log;
import org.apache.lucene.search.exposed.facet.FacetResponse;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.StringReader;
import java.rmi.RemoteException;
import java.util.*;

/**
 * Takes a Summon response as per
 * http://api.summon.serialssolutions.com/help/api/search/response
 * and converts it to a {@link DocumentResponse} and a {@link FacetResponse}.
 * Mapping between field & facet names as well as score adjustments or tag
 * tweaking is not in the scope of this converter.
 * </p><p>
 * The Summon API does not return namespace-aware XML so the parser is equally
 * relaxed.
 */
@QAInfo(level = QAInfo.Level.NORMAL,
        state = QAInfo.State.IN_DEVELOPMENT,
        author = "te")
public class SummonResponseBuilder {
    private static Log log = LogFactory.getLog(SummonResponseBuilder.class);

    /**
     * Recommended behaviour for a Summa document searcher is to provide a
     * recordBase as this makes backtracking easier and allows for optimized
     * lookups.
     * </p><p>
     * Specifying a recordBase results in the field 'recordBase' being added
     * to records in DocumentResponses.
     * </p><p>
     * Optional. Default is 'summon'.
     */
    public static final String CONF_RECORDBASE =
        "summonresponsebuilder.recordbase";
    public static final String DEFAULT_RECORDBASE = "summon";

    private XMLInputFactory xmlFactory = XMLInputFactory.newInstance();
    {
        xmlFactory.setProperty(XMLInputFactory.IS_COALESCING, true);
    }

    private String recordBase = DEFAULT_RECORDBASE;

    public SummonResponseBuilder(Configuration conf) {
        recordBase = conf.getString(CONF_RECORDBASE, recordBase);
        if ("".equals(recordBase)) {
            recordBase = null;
        }
    }

    public long buildResponses(
        Request request, SummonFacetRequest facets,
        ResponseCollection responses,
        String summonResponse) throws XMLStreamException {
        boolean collectdocIDs = request.getBoolean(
            DocumentKeys.SEARCH_COLLECT_DOCIDS, false);
        XMLStreamReader xml;
        try {
            xml = xmlFactory.createXMLStreamReader(new StringReader(
                summonResponse));
        } catch (XMLStreamException e) {
            throw new IllegalArgumentException(
                "Unable to construct a reader from input", e);
        }

        String query = request.getString(DocumentKeys.SEARCH_QUERY);
        String filter = request.getString(DocumentKeys.SEARCH_FILTER, null);
        int startIndex = request.getInt(DocumentKeys.SEARCH_START_INDEX, 0) + 1;

        if (!findTagStart(xml, "response")) {
            log.warn("Could not locate start tag 'response', exiting");
            return 0;
        }
        long searchTime = Long.parseLong(
            getAttribute(xml, "totalRequestTime", "0"));
        long hitCount = Long.parseLong(
            getAttribute(xml, "recordCount", "0"));
        if (!findTagStart(xml, "response")) {
            log.warn("Could not locate start tag 'response', exiting");
            return 0;
        }
        String summonQueryString = "N/A";
        int maxRecords = -1;
        List<DocumentResponse.Record> records = null;
        // Seek to queries, facets or documents
        String currentTag;
        while ((currentTag = jumpToNextTagStart(xml)) != null) {
            if ("query".equals(currentTag)) {
                maxRecords = Integer.parseInt(getAttribute(
                    xml, "pageSize", Integer.toString(maxRecords)));
                summonQueryString = getAttribute(
                    xml, "queryString", summonQueryString);
                continue;
            }
            if ("rangeFacetFields".equals(currentTag) && collectdocIDs) {
                log.warn("Currently there is no support for returning range" 
                         + " facets");
                // TODO: Implement this
            }
            if ("facetFields".equals(currentTag) && collectdocIDs) {
                FacetResult facetResult = extractFacetResult(xml, facets);
                if (facetResult != null) {
                    responses.add(facetResult);
                }
            }
            if ("documents".equals(currentTag)) {
                records = extractRecords(xml);
            }
        }
        DocumentResponse documentResponse = new DocumentResponse(
            filter, query, startIndex, maxRecords, null, false, new String[0],
            searchTime, hitCount);
        for (DocumentResponse.Record record: records) {
            documentResponse.addRecord(record);
        }
        responses.add(documentResponse);
        return documentResponse.getHitCount();
    }

    /**
     * Extracts all facet responses.
     * @param xml    the stream to extract Summon facet information from.
     *               Must be positioned at 'facetFields'.
     * @param facets a definition of the facets to extract.
     * @return a Summa FacetResponse.
     * @throws javax.xml.stream.XMLStreamException if there was an error
     * accessing the xml stream.
     */
    private FacetResult extractFacetResult(
        XMLStreamReader xml, SummonFacetRequest facets)
                                                     throws XMLStreamException {
        HashMap<String, Integer> facetIDs =
            new HashMap<String, Integer>(facets.getFacets().size());
        // 1 facet = 1 field in Summon-world
        HashMap<String, String[]> fields =
            new HashMap<String, String[]>(facets.getFacets().size());
        for (int i = 0 ; i < facets.getFacets().size() ; i++) {
            SummonFacetRequest.Facet facet = facets.getFacets().get(i);
            facetIDs.put(facet.getField(), i);
            // TODO: Consider displayname
            fields.put(facet.getField(), new String[]{facet.getField()});
        }
        final FacetResultExternal summaFacetResult = new FacetResultExternal(
            facets.getMaxTags(), facetIDs, fields);
        iterateElements(xml, "facetFields", "facetField", new XMLCallback() {
            @Override
            public void execute(XMLStreamReader xml) throws XMLStreamException {
                extractFacet(xml, summaFacetResult);
            }

        });
        return summaFacetResult;
    }

    /**
     * Extracts a single facet response and adds it to the facet result.
     * @param xml    the stream to extract Summon facet information from.
     *               Must be positioned at 'facetField'.
     * @param summaFacetResult where to store the extracted facet.
     * @throws javax.xml.stream.XMLStreamException if there was an error
     * accessing the xml stream.
     */
    private void extractFacet(
        XMLStreamReader xml, final FacetResultExternal summaFacetResult)
                                                     throws XMLStreamException {
         // TODO: Consider fieldname and other attributes?
        final String facetName = getAttribute(xml, "displayName", null);
        iterateElements(xml, "facetField", "facetCount", new XMLCallback() {
            @Override
            public void execute(XMLStreamReader xml)  {
                String tagName = getAttribute(xml, "value", null);
                Integer tagCount =
                    Integer.parseInt(getAttribute(xml, "count", "0"));
                summaFacetResult.addTag(facetName, tagName, tagCount);
            }
        });
    }

    private abstract static class XMLCallback {
        public abstract void execute(XMLStreamReader xml)
                                                      throws XMLStreamException;
        public void close() { } // Called when iteration has finished
    }

    /**
     * Iterates over elements in the stream until end element is encountered
     * or end of document is reached. For each element matching actionElement,
     * callback is called.
     * @param xml        the stream to iterate.
     * @param endElement the stopping element.
     * @param actionElement callback is activated when encountering elements
     *                   with this name.
     * @param callback   called for each encountered element.
     * @throws javax.xml.stream.XMLStreamException if the stream could not
     * be iterated or an error occured during callback.
     */
    private void iterateElements(
        XMLStreamReader xml, String endElement, String actionElement,
        XMLCallback callback) throws XMLStreamException {
        while (true) {
            if (xml.getEventType() == XMLStreamReader.END_DOCUMENT
                || (xml.getEventType() == XMLStreamReader.END_ELEMENT
                    && xml.getLocalName().equals(endElement))) {
                break;
            }
            if (xml.getEventType() == XMLStreamReader.START_ELEMENT
                && xml.getLocalName().equals(actionElement)) {
                callback.execute(xml);
            }
            xml.next();
        }
    }
    
    /**
     * Extracts all Summon documents and converts them to
     * {@link }DocumentResponse#Record}.
     * @param xml the stream to extract records from. Must be positioned at
     * ELEMENT_START for "documents".
     * @return an array of record or the empty list if no documents were found.
     * @throws javax.xml.stream.XMLStreamException if there was an error
     * during stream access.
     */
    private List<DocumentResponse.Record> extractRecords(XMLStreamReader xml)
        throws XMLStreamException {
        // Positioned at documents
        final List<DocumentResponse.Record> records =
            new ArrayList<DocumentResponse.Record>(50);
        iterateElements(xml, "documents", "document", new XMLCallback() {
            @Override
            public void execute(XMLStreamReader xml) throws XMLStreamException {
                DocumentResponse.Record record = extractRecord(xml);
                if (record != null) {
                    records.add(record);
                }
            }

        });
        return records;
    }


    /**
     * Extracts a Summon document and converts it to
     * {@link }DocumentResponse#Record}. The compact representation
     * "shortformat" is generated on the fly and added to the list of fields.
     * @param xml the stream to extract the record from. Must be positioned at
     * ELEMENT_START for "document".
     * @return a record or null if no document could be extracted.
     * @throws javax.xml.stream.XMLStreamException if there was an error
     * accessing the xml stream.
     */
    private DocumentResponse.Record extractRecord(XMLStreamReader xml)
                                                    throws XMLStreamException {
    // http://api.summon.serialssolutions.com/help/api/search/response/documents
        String openUrl = getAttribute(xml, "openUrl", null);
        if (openUrl == null) {
            log.warn("Encountered a document without openUrl. Discarding");
            return null;
        }
        String availibilityToken = getAttribute(xml, "availabilityToken", null);
        String hasFullText =       getAttribute(xml, "hasFullText", "false");
        String inHoldings =        getAttribute(xml, "inHoldings", "false");

        final Set<String> wanted = new HashSet<String>(Arrays.asList(
            "ID", "Score", "Title", "Subtitle", "Author", "ContentType",
            "PublicationDate_xml"));
        final ConvenientMap extracted = new ConvenientMap();
        final List<DocumentResponse.Field> fields =
            new ArrayList<DocumentResponse.Field>(50);
        iterateElements(xml, "document", "field", new XMLCallback() {
            @Override
            public void execute(XMLStreamReader xml) throws XMLStreamException {
                DocumentResponse.Field field = extractField(xml);
                if (field!= null) {
                    if (wanted.contains(field.getName())) {
                        extracted.put(field.getName(), field.getContent());
                    }
                    fields.add(field);
                }
            }

        });
        String id = extracted.getString("ID", null);

        if (id == null) {
            log.warn("Unable to locate field 'ID' in Summon document. "
                     + "Skipping document");
            return null;
        }
        fields.add(new DocumentResponse.Field(
            "availibilityToken", availibilityToken, true));
        fields.add(new DocumentResponse.Field(
            "hasFullText", hasFullText, true));
        fields.add(new DocumentResponse.Field(
            "inHoldings", inHoldings, true));

        fields.add(new DocumentResponse.Field(
            "shortformat", createShortformat(extracted), false));

        if (recordBase != null) {
            fields.add(new DocumentResponse.Field(
            "recordBase", recordBase, false));
        }

        DocumentResponse.Record record =
            new DocumentResponse.Record(
                id, "Summon", extracted.getFloat("Score", 0f), null);
        for (DocumentResponse.Field field: fields) {
            record.addField(field);
        }
        return record;
    }

    private String createShortformat(ConvenientMap extracted) {
        // TODO: Incorporate this properly instead of String-hacking
        final StringBuffer shortformat = new StringBuffer(500);
        shortformat.append("  <shortrecord>\n");
        shortformat.append("    <rdf:RDF xmlns:dc=\"http://purl.org/dc/element"
                           + "s/1.1/\" xmlns:rdf=\"http://www.w3.org/1999/02/"
                           + "22-rdf-syntax-ns#\">\n");
        shortformat.append("      <rdf:Description>\n");

        shortformat.append("        <dc:title>").
            append(XMLUtil.encode(extracted.getString("Title", ""))).
            append(" : ").
            append(XMLUtil.encode(extracted.getString("Subtitle", ""))).
            append("</dc:title>\n");
        shortformat.append("        <dc:creator>").
            append(XMLUtil.encode(extracted.getString("Author", ""))).
            append("</dc:creator>\n");
        shortformat.append("        <dc:type xml:lang=\"da\">").
            append(XMLUtil.encode(extracted.getString("ContentType", ""))).
            append("</dc:type>\n");
        shortformat.append("        <dc:type xml:lang=\"en\">").
            append(XMLUtil.encode(extracted.getString("ContentType", ""))).
            append("</dc:type>\n");
        shortformat.append("        <dc:date>").
            append(extracted.getString("PublicationDate_xml", "????")).
            append("</dc:date>\n");


        shortformat.append("        <dc:format></dc:format>\n");
        shortformat.append("      </rdf:Description>\n");
        shortformat.append("    </rdf:RDF>\n");
        shortformat.append("  </shortrecord>\n");
        return shortformat.toString();
    }

    /**
     * Extracts a Summon document field and converts it to
     * {@link }DocumentResponse#Field}.
     * </p><p>
     * While this implementation tries to produce fields for all inputs,
     * it is not guaranteed that it will be usable as no authoritative list
     * of possible micro formats used by Summon has been found.
     * @param xml the stream to extract the field from. Must be positioned at
     * ELEMENT_START for "field".
     * @return a field or null if no field could be extracted.
     * @throws javax.xml.stream.XMLStreamException if there was an error
     * accessing the xml stream.
     */
    private DocumentResponse.Field extractField(XMLStreamReader xml)
        throws XMLStreamException {
        String name = getAttribute(xml, "name", null);
        if (name == null) {
            log.warn("Could not extract name for field. Skipping field");
            return null;
        }
        // TODO: Implement XML handling and multiple values properly
        if (name.endsWith("_xml")) {
            if ("PublicationDate_xml".equals(name)) {
                /*
                  <field name="PublicationDate_xml">
                    <datetime text="20081215" month="12" year="2008" day="15"/>
                  </field>
                 */
                findTagStart(xml, "datetime");
                return new DocumentResponse.Field(name, getAttribute(
                    xml, "text", "????").substring(0, 4), false);
            }

            log.debug(
                "XML fields are not supported yet. Skipping '" + name + "'");
            return null;
        }
        final StringBuffer value = new StringBuffer(50);
        iterateElements(xml, "field", "value", new XMLCallback() {
            @Override
            public void execute(XMLStreamReader xml) throws XMLStreamException {
                value.append(value.length() == 0 ? xml.getElementText() :
                        "\n" + xml.getElementText());
            }
        });
        if (value.length() == 0) {
            log.debug("No value for field '" + name + "'");
            return null;
        }
        return new DocumentResponse.Field(name, value.toString(), true);
    }

    /**
     * Skips everything until a start tag is reacted or the readers is depleted.
     * @param xml the stream to iterate over.
     * @return the name of the start tag or null if EOD.
     * @throws javax.xml.stream.XMLStreamException if there was an error
     * accessing the xml stream.
     */
    private String jumpToNextTagStart(XMLStreamReader xml)
        throws XMLStreamException {
        if (xml.getEventType() == XMLStreamReader.START_ELEMENT) {
            xml.next(); // Skip if already located at a start
        }
        while (true) {
            if (xml.getEventType() == XMLStreamReader.START_ELEMENT) {
                return xml.getLocalName();
            }
            if (xml.getEventType() == XMLStreamReader.END_DOCUMENT) {
                return null;
            }
            xml.next();
        }
    }

    private String getAttribute(
        XMLStreamReader xml, String attributeName, String defaultValue) {
        for (int i = 0 ; i < xml.getAttributeCount() ; i++) {
            if (xml.getAttributeLocalName(i).equals(attributeName)) {
                return xml.getAttributeValue(i);
            }
        }
        return defaultValue;
    }

    /**
     * Iterates over the xml until a start tag with startTagName is reached.
     * @param xml          the stream to iterate over.
     * @param startTagName the name of the tag to locate.
     * @return true if the tag was found, else false.
     * @throws javax.xml.stream.XMLStreamException if there were an error
     * seeking the xml stream.
     */
    private boolean findTagStart(
        XMLStreamReader xml, String startTagName) throws XMLStreamException {
        while (true)  {
            if (xml.getEventType() == XMLStreamReader.START_ELEMENT
                && startTagName.equals(xml.getLocalName())) {
                return true;
            }
            if (xml.getEventType() == XMLStreamReader.END_DOCUMENT) {
                return false;
            }
            try {
                xml.next();
            } catch (XMLStreamException e) {
                throw new XMLStreamException(
                    "Error seeking to start tag for element '" + startTagName
                    + "'", e);
            }
        }
    }
}