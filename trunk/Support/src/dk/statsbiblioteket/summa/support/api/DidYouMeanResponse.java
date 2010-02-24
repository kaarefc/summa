package dk.statsbiblioteket.summa.support.api;

import dk.statsbiblioteket.summa.search.api.Response;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.Serializable;
import java.io.StringWriter;
import java.util.LinkedList;

/**
 * FIXME: Missing class docs for dk.statsbiblioteket.summa.support.api.DidYouMeanResponse
 *
 * @author Mikkel Kamstrup Erlandsen <mailto:mke@statsbiblioteket.dk>
 * @since Feb 9, 2010
 */
public class DidYouMeanResponse implements Response {
    /**
     * Did-You-Mean response name.
     */
    public static final String DIDYOUMEANRESPONSE = "DidYouMeanResponse";

    /**
     * Did-You-Mean XML namespace.
     */
    public static final String NAMESPACE =
            "http://statsbiblioteket.dk/summa/2009/DidYouMeanResponse";
    /**
     * Did-You-Mean XML version.
     */
    public static final String VERSION = "1.0";

    /**
     * Did-You-Mean XML response tag.
     */
    public static final String DIDYOUMEAN_RESPONSE = "DidYouMeanResponse";
    @SuppressWarnings({"DuplicateStringLiteralInspection"})
    public static final String VERSION_TAG = "version";
    @SuppressWarnings({"DuplicateStringLiteralInspection"})
    /**
     * Did-You-Mean query XML tag.
     */
    public static final String QUERY_TAG = "query";

    /**
     * Did-You-Mean XML tag.
     */
    public static final String DIDYOUMEAN = "didyoumean";

    /**
     * Local XML output factory.
     */
    private static XMLOutputFactory xmlOutputFactory =
                                                XMLOutputFactory.newInstance();
    /**
     * Search query.
     */
    private String query = null;

    /**
     * Local linkedList, which is used for storage of query results.
     */
    private LinkedList<ResultTuple> resultTuples = null;

    /**
     * Did-You-Mean response constructor.
     * @param query the query.
     */
    public DidYouMeanResponse(String query) {
        this.query = query;
        resultTuples = new LinkedList<ResultTuple>();
    }

    /**
     * Getter for Did-You-Mean name.
     * @return {@link DidYouMeanResponse#DIDYOUMEANRESPONSE}
     */
    @Override
    public String getName() {
        return DIDYOUMEANRESPONSE;
    }

    /**
     * Should merge multiple responses together, used for distributed searc.
     * @param other the Response to merge into this.
     * @throws ClassCastException
     */
    @Override
    public void merge(Response other) throws ClassCastException {
        // TODO something nice to other.
    }

    /**
     * Converts response to an XML document. Eg.
     * <pre>
     * <?xml version="1.0" encoding="UTF-8" ?>
     * <DidYouMeanResponse xmlns="http://statsbiblioteket.dk/summa/2009/DidYouMeanResponse" query="foobaw" version="1.0">
     *  <didyoumean score="0.6666666666666667">koobas</didyoumean>
     *  <didyoumean score="0.6666666666666667">boobar</didyoumean>
     * </DidYouMeanResponse>
     * </pre>
     * @return XML block as a string.
     */
    @Override
    public String toXML() {
        StringWriter sw = new StringWriter(2000);
        XMLStreamWriter writer;
        try {
            writer = xmlOutputFactory.createXMLStreamWriter(sw);
        } catch (XMLStreamException e) {
            throw new RuntimeException(
                    "Unable to create XMLStreamWriter from factory", e);
        }
        // Write XML document.
        try {
            writer.setDefaultNamespace(NAMESPACE);
            writer.writeStartElement(DIDYOUMEAN_RESPONSE);
            writer.writeDefaultNamespace(NAMESPACE);
            writer.writeAttribute(VERSION_TAG, VERSION);
            writer.writeAttribute(QUERY_TAG, query);
            writer.writeCharacters("\n");
            for(ResultTuple tuple: resultTuples) {
                writer.writeCharacters("    ");
                writer.writeStartElement(DIDYOUMEAN);
                writer.writeAttribute("score", String.valueOf(tuple.getScore()));
                writer.writeCharacters(tuple.getResult());
                writer.writeEndElement();
                writer.writeCharacters("\n");
            }
            writer.writeEndElement();
            writer.writeCharacters("\n");
            writer.writeEndDocument();
            
            writer.flush(); // Just to make sure
        } catch (XMLStreamException e) {
            throw new RuntimeException(
                    "Got XMLStreamException while constructing XML from "
                    + "DidYouMeanResponse", e);
        }
        return sw.toString();
    }

    /**
     * Add a result to result list.
     * @param result the result to add.
     * @param score the result score.
     * @param corpusQueryResults the corpusQuery for the result.
     */
    public void addResult(String result, double score, int corpusQueryResults) {
        resultTuples.addFirst(new ResultTuple(result, score,
                                                           corpusQueryResults));
    }

    /**
     * Private ResultTupleClass. Used for storing multiple values associated
     * with the result for a given query. These values are score and
     * corpusQuery.
     */
    private static class ResultTuple implements Serializable {
        // result
        private String result = null;
        // result score
        private double score = 0d;
        // result corpusQuery
        private int corpusQueryResults = 0;

        /**
         * Result Tuple Constructor.
         * @param result the result.
         * @param score result score.
         * @param corpusQueryResults result corpusQuery.
         */
        public ResultTuple(String result, double score,
                                                       int corpusQueryResults) {
            this.result = result;
            this.score = score;
            this.corpusQueryResults = corpusQueryResults;
        }

        /**
         * Getter for result.
         * @return the result.
         */
        public String getResult() {
            return result;
        }

        /**
         * Getter for score.
         * @return the result score.
         */
        public double getScore() {
            return score;
        }

        /**
         * Getter for corpusQuery.
         * @return the corpusQuery for the result.
         */
        public int getCorpusQueryResults() {
            return corpusQueryResults;
        }
    }

}
