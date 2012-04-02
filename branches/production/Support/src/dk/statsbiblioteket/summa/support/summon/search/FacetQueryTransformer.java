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
package dk.statsbiblioteket.summa.support.summon.search;

import dk.statsbiblioteket.summa.common.configuration.Configuration;
import dk.statsbiblioteket.summa.search.tools.QueryRewriter;
import dk.statsbiblioteket.util.qa.QAInfo;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.logging.Log;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;

import java.io.StringWriter;
import java.util.*;

/**
 * Utility class for converting filter queries into facet queries conforming to the Solr API:
 * http://api.summon.serialssolutions.com/help/api/search/parameters/facet-value-filter
 * </p><p>
 * Note that a complete transformation is not possible, e.g. {@code moo:boo NOT (foo:bar AND zoo:baz)}.
 * In case of incomplete transformations, the whole result is discarded.
 * </p>
 */
@QAInfo(level = QAInfo.Level.NORMAL,
        state = QAInfo.State.IN_DEVELOPMENT,
        author = "te")
public class FacetQueryTransformer {
    private static Log log = LogFactory.getLog(FacetQueryTransformer.class);

    static final QueryParser qp = QueryRewriter.createDefaultQueryParser();
    static final QueryRewriter qr = new QueryRewriter(Configuration.newMemoryBased(), null, null);

    @SuppressWarnings({"UnusedParameters"})
    public FacetQueryTransformer(Configuration conf) {

    }

    /**
     * Queries must consist of BooleanQueries, TermQueries and PhraseQueries only. Samples:<br/>
     * {@code 'foo:bar'}<br/>
     * {@code 'foo:bar -zoo:moo'}<br/>
     * {@code 'foo:bar -(zoo:moo OR ulk:tap)'}<br/>
     * {@code 'foo:bar' +(zoo:moo AND ulk:tap)'}
     * </p><p>
     * Note that negative sub-BooleanQueries containing AND and positive sub-queries containing OR cannot be translated
     * to the solr facet-value-filter, nor the facet-value-group-filter as it does not span facets. If such a construct
     * is encountered, it will be returned.<br/>
     * Example of problematic queries:<br/>
     * {@code 'foo:bar -(zoo:moo AND ulk:tap)'}<br/>
     * {@code 'foo:bar +(zoo:moo OR ulk:tap)'}
     * </p><p>
     * @param query a facet oriented query.
     * @return the generated solrRequest or null if the transformation could not be completed.
     * @throws org.apache.lucene.queryparser.classic.ParseException if the query could not be parsed at all.
     */
    public Map<String, List<String>> convertQueryToFacet(String query) throws ParseException {
        log.debug("Converting query '" + query + "' to facet queries");
        Map<String, List<String>> solrRequest = new HashMap<String, List<String>>(20);
        if ("".equals(query) || query == null) {
            return solrRequest;
        }
        return convertQueryToFacet(qp.parse(query), solrRequest, false) ? solrRequest : null;
    }

    // True if success
    private boolean convertQueryToFacet(
        Query query, Map<String, List<String>> solrRequest, boolean negated) throws ParseException {
        if (query instanceof TermQuery) {
            convertTermQuery(solrRequest, (TermQuery)query, negated);
            return true;
        }
        if (query instanceof PhraseQuery) {
            convertPhraseQuery(solrRequest, (PhraseQuery) query, negated);
            return true;
        }

        // "-(a OR b)" -> "-a -b"
        // "+(a b)" -> "+a +b"
        // "a -(b c)" -> boom
        if (query instanceof BooleanQuery && !negated) { // Only MUST or MUST_NOT allowed
            for (BooleanClause clause: ((BooleanQuery)query).getClauses()) { // Check for MUST
                if (clause.getOccur() == BooleanClause.Occur.SHOULD) {
                    if (log.isDebugEnabled()) {
                        log.debug("Encountered " + clause.getOccur() + " in BooleanClause '" + clause + "' where only "
                                  + "MUST or MUST_NOT were acceptable in query segment '" + qr.toString(query) + "'");
                    }
                    return false;
                }
                if (!convertQueryToFacet(
                    clause.getQuery(), solrRequest, clause.getOccur() == BooleanClause.Occur.MUST_NOT)) {
                    return false;
                }
            }
            return true;
        }
        if (query instanceof BooleanQuery && negated) { // Only SHOULD allowed
            for (BooleanClause clause: ((BooleanQuery)query).getClauses()) {
                if (clause.getOccur() != BooleanClause.Occur.SHOULD) {
                    if (log.isDebugEnabled()) {
                        log.debug("Encountered " + clause.getOccur() + " in BooleanClause '" + clause + "' where only "
                                  + "SHOULD were acceptable in query segment '" + qr.toString(query) + "'");
                    }
                    return false;
                }
                if (!convertQueryToFacet(
                    clause.getQuery(), solrRequest, true)) {
                    return false;
                }
            }
            return true;
        }
        throw new ParseException("Only TermQuery, PhraseQuery and BooleanQuery are supported. Got '"
                                 + query.getClass().getSimpleName() + " from query '" + query + "'");
    }

    private void convertPhraseQuery(
        Map<String, List<String>> querymap, PhraseQuery pq, boolean negated) throws ParseException {
        if (pq.getTerms()[0].field() == null || "".equals(pq.getTerms()[0].field())) {
            throw new ParseException("Encountered PhraseQuery without field '" + pq + "'");
        }
        StringWriter terms = new StringWriter(100);
        boolean first = true;
        for (Term term: pq.getTerms()) {
            if (term.text() == null || "".equals(term.text())) {
                throw new ParseException("Encountered Term without text '" + pq + "' in PhraseQuery '" + pq + "'");
            }
            if (first) {
                first = false;
            } else {
                terms.append(" "); // This should work as we use a plain WhiteSpaceAnalyzer for tokenization
            }
            terms.append(term.text());
        }
        appendPut(querymap, "s.fvf", pq.getTerms()[0].field() + "," + terms + "," + negated);
    }

    private void convertTermQuery(
        Map<String, List<String>> querymap, TermQuery tq, boolean negated) throws ParseException {
        if (tq.getTerm().field() == null || "".equals(tq.getTerm().field())) {
            throw new ParseException("Encountered TermQuery without field '" + tq + "'");
        }
        if (tq.getTerm().text() == null || "".equals(tq.getTerm().text())) {
            throw new ParseException("Encountered TermQuery without text '" + tq + "'");
        }
        appendPut(querymap, "s.fvf", tq.getTerm().field() + "," + tq.getTerm().text() + "," + negated);
    }

    private void appendPut(Map<String, List<String>> querymap, String key, String... values) {
        List<String> existing = querymap.get(key);
        if (existing == null) {
            querymap.put(key, Arrays.asList(values));
            return;
        }
        if (!(existing instanceof ArrayList)) {
            existing = new ArrayList<String>(existing);
            querymap.put(key, existing);
        }
        existing.addAll(Arrays.asList(values));
    }
}
