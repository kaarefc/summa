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
package dk.statsbiblioteket.summa.search;

import dk.statsbiblioteket.summa.common.configuration.Configuration;
import dk.statsbiblioteket.summa.common.configuration.SubConfigurationsNotSupportedException;
import dk.statsbiblioteket.summa.search.api.QueryException;
import dk.statsbiblioteket.summa.search.api.Request;
import dk.statsbiblioteket.summa.search.api.ResponseCollection;
import dk.statsbiblioteket.summa.search.api.document.DocumentKeys;
import dk.statsbiblioteket.summa.search.tools.QueryFuzzinator;
import dk.statsbiblioteket.summa.search.tools.QueryPhraser;
import dk.statsbiblioteket.summa.search.tools.QueryRewriter;
import dk.statsbiblioteket.summa.search.tools.QuerySanitizer;
import dk.statsbiblioteket.summa.support.harmonise.hub.QueryReducer;
import dk.statsbiblioteket.util.Strings;
import dk.statsbiblioteket.util.qa.QAInfo;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.lucene.queryparser.classic.ParseException;

import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.List;

/**
 * A search node wrapper that sanitizes queries and filters for common query syntax errors.
 * @see {@link dk.statsbiblioteket.summa.search.tools.QuerySanitizer} for details.
 */
@QAInfo(level = QAInfo.Level.NORMAL,
        state = QAInfo.State.IN_DEVELOPMENT,
        author = "te")
public class QueryRewritingSearchNode implements SearchNode {
    private static Log log = LogFactory.getLog(QueryRewritingSearchNode.class);

    /**
     * Whether or not incoming queries should be sanitized.
     * </p><p>
     * Optional. Default is true.
     */
    public static final String CONF_SANITIZE_QUERIES = "rewriter.sanitizequeries";
    public static final String SEARCH_SANITIZE_QUERIES = CONF_SANITIZE_QUERIES;
    public static final boolean DEFAULT_SANITIZE_QUERIES = true;

    /**
     * Whether or not incoming filters should be sanitized.
     * </p><p>
     * Optional. Default is true.
     */
    public static final String CONF_SANITIZE_FILTERS = "rewriter.sanitizefilters";
    public static final String SEARCH_SANITIZE_FILTERS = CONF_SANITIZE_FILTERS;
    public static final boolean DEFAULT_SANITIZE_FILTERS = true;

    /**
     * Whether or not to add a phrase query component to the query. This is primarily intended for searchers with a
     * simple query parser, such as Lucene's default. For more advanced query parsers that supports DisMax or
     * similar, no phrase should be added.
     * </p><p>
     * Optional. Default is false.
     * @see {@link dk.statsbiblioteket.summa.search.tools.QueryPhraser} for details.
     */
    public static final String CONF_PHRASE_QUERIES = "rewriter.phrasequeries";
    public static final String SEARCH_PHRASE_QUERIES = CONF_PHRASE_QUERIES;
    public static final boolean DEFAULT_PHRASE_QUERIES = false;

    /**
     * If true, queries or filters which has been sanitized are passed through the QueryRewriter in order to normalize
     * the output. Tweaks to normalizing are specified with the properties from {@link QueryRewriter}.
     * </p><p>
     * Optional. Default is false.
     */
    public static final String CONF_SANITIZE_NORMALIZE = "rewriter.sanitize.normalize";
    public static final String SEARCH_SANITIZE_NORMALIZE = CONF_SANITIZE_NORMALIZE;
    public static final boolean DEFAULT_SANITIZE_NORMALIZE = true;

    /**
     * If true, queries will be passed through a {@link QueryReducer}. See the QueryReducer for setup details.
     * </p><p>
     * Important: The Query Reducer works in limited mode for this SearchNode (full mode only available with the
     * Solr hub framework). The setup for the reducer only has affect if the
     * {@link dk.statsbiblioteket.summa.support.harmonise.hub.QueryReducer.ReducerTarget#CONF_COMPONENT_ID} is
     * not defined, thereby making the reducer active for all queries.
     */
    public static final String CONF_REDUCE = "rewriter.reduce";
    public static final String SEARCH_REDUCE = CONF_REDUCE;
    public static final boolean DEFAULT_REDUCE = true;

    /**
     * If true, queries are routed through {@link dk.statsbiblioteket.summa.search.tools.QueryFuzzinator}.
     * </p><p>
     * Optional. Default is false.
     */
    public static final String CONF_FUZZY_QUERIES = "rewriter.fuzzyqueries";
    public static final boolean DEFAULT_FUZZY_QUERIES = false;

    /**
     * If specified, the designation can be used as a prefix for the search parameters to ensure that only the
     * specific QueryRewritingSearchNode receives the parameters.
     */
    public static final String CONF_DESIGNATION = "rewriter.designation";

    /**
     * A sub-configuration with the setup for the SearchNode that is to be created and used for all calls.
     * The configuration must contain the property
     * {@link dk.statsbiblioteket.summa.search.SearchNodeFactory#CONF_NODE_CLASS} as
     * {@link dk.statsbiblioteket.summa.search.SearchNodeFactory} is used for creating the single inner node.
     * </p><p>
     * Mandatory.
     */
    public static final String CONF_INNER_SEARCHNODE = "queryrewriter.inner.searchnode";

    /**
     * If true, queries reduced to the empty String are kept instead of resulting in no query key-value pair.
     * </p><p>
     * Optional. Default is true.
     */
    public static final String CONF_KEEP_EMPTY_QUERIES = "queryrewriter.query.nomatchisempty";
    public static final boolean DEFAULT_KEEP_EMPTY_QUERIES = true;

    /**
     * If true, filters reduced to the empty String are kept instead of resulting in no filter key-value pair.
     * </p><p>
     * Optional. Default is true.
     */
    public static final String CONF_KEEP_EMPTY_FILTERS = "queryrewriter.filter.nomatchisempty";
    public static final boolean DEFAULT_KEEP_EMPTY_FILTERS = true;

    private final boolean sanitizeQueries;
    private final boolean sanitizeFilters;
    private final boolean phrasequeries;
    private final boolean normalize;
    private final boolean reduce;
    private final SearchNode inner;
    private final QuerySanitizer sanitizer;
    private final QueryRewriter normalizer;
    private final QueryPhraser queryPhraser;
    private final QueryFuzzinator fuzzinator;
    private final QueryReducer reducer;
    private final String prefix;
    private final boolean keepEmptyQueries;
    private final boolean keepEmptyFilters;

    public QueryRewritingSearchNode(Configuration conf) {
        this(conf, resolveInner(conf));
    }

    private static SearchNode resolveInner(Configuration conf) {
        if (!conf.valueExists(CONF_INNER_SEARCHNODE)) {
            throw new ConfigurationException(
                "No inner search node defined. A proper sub-configuration must exist for key " + CONF_INNER_SEARCHNODE);
        }
        try {
            return SearchNodeFactory.createSearchNode(conf.getSubConfiguration(CONF_INNER_SEARCHNODE));
        } catch (RemoteException e) {
            throw new ConfigurationException("Unable to create inner search node, although a value were present for " +
                                             "key " + CONF_INNER_SEARCHNODE, e);
        } catch (SubConfigurationsNotSupportedException e) {
            throw new ConfigurationException(
                "A configuration with support for sub configurations must be provided for the adjuster and must "
                + "contain a sub configuration with key " + CONF_INNER_SEARCHNODE, e);
        }
    }

    QueryRewritingSearchNode(Configuration conf, SearchNode inner) {
        this.inner = inner;
        sanitizer = new QuerySanitizer(conf);
        sanitizeQueries = conf.getBoolean(CONF_SANITIZE_QUERIES, DEFAULT_SANITIZE_QUERIES);
        sanitizeFilters = conf.getBoolean(CONF_SANITIZE_FILTERS, DEFAULT_SANITIZE_FILTERS);
        normalize = conf.getBoolean(CONF_SANITIZE_NORMALIZE, DEFAULT_SANITIZE_NORMALIZE);
        reduce = conf.getBoolean(CONF_REDUCE, DEFAULT_REDUCE);

        queryPhraser = new QueryPhraser(conf);
        fuzzinator = conf.getBoolean(CONF_FUZZY_QUERIES, DEFAULT_FUZZY_QUERIES) ? new QueryFuzzinator(conf) : null;
        normalizer = new QueryRewriter(conf, QueryRewriter.createDefaultQueryParser(), new QueryRewriter.Event());
        reducer = conf.getBoolean(CONF_REDUCE, DEFAULT_REDUCE) ? new QueryReducer(conf) : null;
        phrasequeries = conf.getBoolean(CONF_PHRASE_QUERIES, DEFAULT_PHRASE_QUERIES);
        prefix = conf.valueExists(CONF_DESIGNATION) && !"".equals(conf.getString(CONF_DESIGNATION)) ?
                 conf.getString(CONF_DESIGNATION) + "." : "";
        keepEmptyQueries = conf.getBoolean(CONF_KEEP_EMPTY_QUERIES, DEFAULT_KEEP_EMPTY_QUERIES);
        keepEmptyFilters = conf.getBoolean(CONF_KEEP_EMPTY_FILTERS, DEFAULT_KEEP_EMPTY_FILTERS);
        log.debug("Created " + this);
    }

    private Request process(Request request) throws ParseException {
        final String oldQuery = getAlsoEmpty(request, DocumentKeys.SEARCH_QUERY, null);
        if (oldQuery != null && request.getBoolean(prefix + SEARCH_SANITIZE_QUERIES,
                                                   request.getBoolean(SEARCH_SANITIZE_QUERIES, sanitizeQueries))) {
            if ("".equals(oldQuery)) {
                log.debug("Input query was empty. Removing " + DocumentKeys.SEARCH_QUERY + " from request");
                request.remove(DocumentKeys.SEARCH_QUERY);
            } else {
                String newQuery = sanitizer.sanitize(oldQuery).getLastQuery();
                if (reducer != null) {
                    newQuery = reducer.reduce(null, newQuery);
                }
                if (fuzzinator != null) {
                    newQuery = fuzzinator.rewrite(request, newQuery);
                }
                if (request.getBoolean(prefix + SEARCH_SANITIZE_NORMALIZE,
                                       request.getBoolean(SEARCH_SANITIZE_NORMALIZE, normalize))) {
                    newQuery = normalizer.rewrite(newQuery);
                }
                if (!keepEmptyQueries && newQuery.isEmpty()) {
                    request.remove(DocumentKeys.SEARCH_QUERY);
                } else {
                    request.put(DocumentKeys.SEARCH_QUERY, newQuery);
                }
                if (oldQuery.equals(newQuery)) {
                    log.debug("Sanitized query is unchanged: '" + oldQuery + "'");
                } else {
                    log.debug("Sanitized query '" + oldQuery + "' to '" + newQuery + "'");
                }
            }
        }
        final List<String> oldFilters = request.getStrings(DocumentKeys.SEARCH_FILTER, new ArrayList<String>());
                //getAlsoEmpty(request, DocumentKeys.SEARCH_FILTER, null);
        if (!oldFilters.isEmpty() // TODO: Feedback should bubble to front end
            && request.getBoolean(prefix + SEARCH_SANITIZE_FILTERS,
                                  request.getBoolean(SEARCH_SANITIZE_FILTERS, sanitizeFilters))) {
            if (oldFilters.size() == 1 && oldFilters.get(0).isEmpty()) {
                log.debug("Input filter was empty. Removing " + DocumentKeys.SEARCH_FILTER + " from request");
                request.remove(DocumentKeys.SEARCH_FILTER);
            } else {
                ArrayList<String> newFilters = new ArrayList<>(oldFilters.size());
                for (String oldFilter: oldFilters) {
                    String newFilter = sanitizer.sanitize(oldFilter).getLastQuery();
                    if (reducer != null) {
                        newFilter = reducer.reduce(null, newFilter);
                    }
                    if (request.getBoolean(prefix + SEARCH_SANITIZE_NORMALIZE,
                                           request.getBoolean(SEARCH_SANITIZE_NORMALIZE, normalize))) {
                        newFilter = normalizer.rewrite(newFilter);
                    }
                    if (keepEmptyFilters || !newFilter.isEmpty()) {
                        newFilters.add(newFilter);
                    }
                }
                if (Strings.join(oldFilters).equals(Strings.join(newFilters))) {
                    log.debug("Sanitized filters are unchanged: [" + Strings.join(oldFilters) + "]");
                } else {
                    log.debug("Sanitized filters [" + Strings.join(oldFilters) + "] to ["
                              + Strings.join(newFilters) + "]");
                }
                request.put(DocumentKeys.SEARCH_FILTER, newFilters);
            }
        }
        final String query = request.getString(DocumentKeys.SEARCH_QUERY, null);
        if (query != null && request.getBoolean(prefix + SEARCH_PHRASE_QUERIES,
                                                request.getBoolean(SEARCH_PHRASE_QUERIES, phrasequeries))) {
            try {
                if (log.isDebugEnabled()) {
                    log.debug(prefix + SEARCH_PHRASE_QUERIES + "="
                              + request.getBoolean(prefix + SEARCH_PHRASE_QUERIES, null) + ", "
                              + SEARCH_PHRASE_QUERIES + "=" + request.getBoolean(SEARCH_PHRASE_QUERIES, phrasequeries));
                }
                request.put(DocumentKeys.SEARCH_QUERY, queryPhraser.rewrite(request, query));
            } catch (ParseException e) {
                log.debug("The QueryPhraser threw a ParseException on '" + query
                          + "'. The query will be passed unmodified", e);
            }
        }
        return request;
    }

    private String getAlsoEmpty(Request request, String key, String defaultValue) {
        String result = request.getString(key, defaultValue);
        return result == null && request.containsKey(key) ? "" : result;
    }

    /* Delegations below */

    @Override
    public void search(Request request, ResponseCollection responses) throws RemoteException {
        try {
            inner.search(process(request), responses);
        } catch (ParseException e) { // Lucene query parser throws this
            throw new QueryException("QueryRewritingSearchNode", request.toString(true), e.getMessage());
        }
    }

    @Override
    public void warmup(String request) {
        inner.warmup(request);
    }

    @Override
    public void open(String location) throws RemoteException {
        inner.open(location);
    }

    @Override
    public void close() throws RemoteException {
        inner.close();
    }

    @Override
    public int getFreeSlots() {
        return inner.getFreeSlots();
    }

    @Override
    public String toString() {
        return "QueryRewritingSearchNode(sanitizeQueries=" + sanitizeQueries + ", sanitizeFilters=" + sanitizeFilters +
               ", phrasequeries=" + phrasequeries + ", normalize=" + normalize + ", prefix='" + prefix
               + "', fuzzyQueries=" + fuzzinator + ", reducer=" + reducer + ", inner=" + inner
               + ", keepEmptyQueries=" + keepEmptyQueries + ", keepEmptyFilters=" + keepEmptyFilters + ")";
    }
}
