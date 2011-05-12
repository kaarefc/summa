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
package dk.statsbiblioteket.summa.support.enrich;

import dk.statsbiblioteket.summa.common.Logging;
import dk.statsbiblioteket.summa.common.Record;
import dk.statsbiblioteket.summa.common.configuration.Configuration;
import dk.statsbiblioteket.summa.common.configuration.Resolver;
import dk.statsbiblioteket.summa.common.filter.Payload;
import dk.statsbiblioteket.summa.common.filter.object.ObjectFilterImpl;
import dk.statsbiblioteket.summa.common.filter.object.PayloadException;
import dk.statsbiblioteket.summa.common.rpc.ConnectionConsumer;
import dk.statsbiblioteket.summa.common.util.RecordUtil;
import dk.statsbiblioteket.summa.search.api.*;
import dk.statsbiblioteket.summa.search.api.document.DocumentKeys;
import dk.statsbiblioteket.summa.search.api.document.DocumentResponse;
import dk.statsbiblioteket.util.Files;
import dk.statsbiblioteket.util.Strings;
import dk.statsbiblioteket.util.qa.QAInfo;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Uses an external SummaSearcher to resolve relations for the given Payload.
 * The resolved relation-IDs are assigned as parents or children to the Record
 * in the Payload before it is passed on in the chain.
 * </p><p>
 * Connection to the searcher is handled by a {@link SearchClient} where the
 * connection setup is defined by the configuration parameter
 * {@link ConnectionConsumer#CONF_RPC_TARGET} and optionally those from.
 * {@link dk.statsbiblioteket.summa.common.rpc.GenericConnectionFactory}.
 * </p><p>
 * In order to perform a search, a query is needed. The essential ingredient is
 * the searchvalue, which is extracted from the Record's metadata. Normally one
 * would precede the RelationResolver with a RecordShaperFilter or
 * similar filter that ensures that the required metadata is present.
 */
@QAInfo(level = QAInfo.Level.NORMAL,
        state = QAInfo.State.IN_DEVELOPMENT,
        author = "te")
public class RelationResolver extends ObjectFilterImpl {
    private static Log log = LogFactory.getLog(RelationResolver.class);

    /**
     * The field to use for searches.
     * The query will be "searchfield:searchvalue", unless the search field is
     * empty, in which case the query will be "searchvalue".
     * </p><p>
     * Optional. Default is no field.
     * @see {link #CONF_SEARCH_METAKEY}.
     */
    public static final String CONF_SEARCH_FIELD =
        "relationresolver.search.field";
    public static final String DEFAULT_SEARCH_FIELD = "";

    /**
     * The record metadata keys for the searchvalue value. One search will be
     * performed for each key.
     * </p><p>
     * Mandatory. This must be 1 or more keys for the Record meta data map.
     */
    public static final String CONF_SEARCH_METAKEYS =
        "relationresolver.search.metakeys";

    /**
     * The maximum number of hits to receive. The Record will be assigned as
     * to each of the documents from the returned hits.
     * </p><p>
     * Optional. Default is 1.
     */
    public static final String CONF_SEARCH_MAXHITS = 
        "relationresolver.search.maxhits";
    public static final int DEFAULT_SEARCH_MAXHITS = 1;

    /**
     * If true, Payloads that cannot be matched to at least 1 ID from the
     * searcher is discarded. If false, the Payload is passed on unmodified.
     * </p><p>
     * Optional. Default is true.
     */
    public static final String CONF_NONMATCHED_DISCARD =
        "relationresolver.nonmatched.discard";
    public static final boolean DEFAULT_NONMATCHED_DISCARD = true;

    /**
     * If an error occurs or (the Payload cannot be matched and
     * {@link #CONF_NONMATCHED_DISCARD} is true), the content of the Record in
     * the Payload is dumped in this folder.
     * </p><p>
     * If this property is blank, Payloads with Records with problems will be
     * discarded. The location is resolved relative to the persistent folder.
     * </p><p>
     * Optional. Default is "relations/nonmatched".
     */
    public static final String CONF_NONMATCHED_FOLDER =
        "relationresolver.nonmatched.folder";
    public static final String DEFAULT_NONMATCHED_FOLDER =
        "relations/nonmatched";

    /**
     * If true, resolved relation-IDs are assigned as parents to the Record.
     * </p><p>
     * Optional. Default is true.
     */
    public static final String CONF_ASSIGN_PARENTS = 
        "relationresolver.assign.parents";
    public static final boolean DEFAULT_ASSIGN_PARENTS = true; 
    
    /**
     * If true, resolved relation-IDs are assigned as children to the Record.
     * </p><p>
     * Optional. Default is false.
     */
    public static final String CONF_ASSIGN_CHILDREN = 
        "relationresolver.assign.children";
    public static final boolean DEFAULT_ASSIGN_CHILDREN = false; 
    
    private final SummaSearcher searcher;
    private final String searchField;
    private final int maxHits;
    private final File nonmatchedFolder;
    private final List<String> metaKeys;
    private final boolean discardNonmatched;
    private final boolean assignAsParents;
    private final boolean assignAsChildren;

    public RelationResolver(Configuration conf) {
        super(conf);
        searchField = conf.getString(CONF_SEARCH_FIELD, DEFAULT_SEARCH_FIELD);
        maxHits = conf.getInt(CONF_SEARCH_MAXHITS, DEFAULT_SEARCH_MAXHITS);
        metaKeys = conf.getStrings(CONF_SEARCH_METAKEYS);
        assignAsParents = conf.getBoolean(
            CONF_ASSIGN_PARENTS, DEFAULT_ASSIGN_PARENTS);
        assignAsChildren = conf.getBoolean(
            CONF_ASSIGN_CHILDREN, DEFAULT_ASSIGN_CHILDREN);
        if (assignAsParents && assignAsChildren) {
            log.warn(String.format(
                "Both %s and %s is true. This normally does not make sense. "
                + "It creates cycles in the Storage which is not a well-tested",
                CONF_ASSIGN_PARENTS, CONF_ASSIGN_CHILDREN));
        }
        if (!assignAsParents && !assignAsChildren) {
            log.warn(String.format(
                "Both %s and %s is false. No assignment will be performed, " 
                + "making this filter inactive except for side-effects such as " 
                + "validation and search-testing",
                CONF_ASSIGN_PARENTS, CONF_ASSIGN_CHILDREN));
        }
        discardNonmatched = conf.getBoolean(
            CONF_NONMATCHED_DISCARD, DEFAULT_NONMATCHED_DISCARD);
        if (metaKeys == null || metaKeys.size() == 0) {
            throw new ConfigurationException(
                "No values for key '" + CONF_SEARCH_METAKEYS + "' present");
        }
        String non = conf.getString(
            CONF_NONMATCHED_FOLDER, DEFAULT_NONMATCHED_FOLDER);
        if (non == null || "".equals(non)) {
            log.debug("No folder for non-matched Records");
            nonmatchedFolder = null;
        } else {
            File nonF = Resolver.getPersistentFile(new File(non));
            if (nonF == null) {
                throw new ConfigurationException(
                    "Unable to resolve non-matching folder '" + non
                    + "' from property " + CONF_NONMATCHED_FOLDER);
            }
            if (!nonF.exists()) {
                if (!nonF.mkdirs()) {
                    throw new ConfigurationException(
                        "Unable to create non-matching folder '" + nonF + "'");
                }
            }
            nonmatchedFolder = nonF;
        }
        if (maxHits == 0) {
            log.warn("maxHits is 0, effectively making RelationResolver a "
                     + "discard-all filter");
        }
        searcher = createSearchClient(conf);
        log.info(String.format(
            "Created RelationResolver with searcher '%s', searchField '%s', "
            + "maxHits %d, metaKey '%s', discard non-matched %b and " 
            + "non-matching folder '%s'",
            conf.getString(ConnectionConsumer.CONF_RPC_TARGET), searchField,
            maxHits, Strings.join(metaKeys, ", "),
            discardNonmatched, nonmatchedFolder));
    }

    protected SummaSearcher createSearchClient(Configuration conf) {
        return new SearchClient(conf);
    }

    @Override
    protected boolean processPayload(Payload payload) throws PayloadException {
        log.trace("processPayload(...) called");
        if (payload.getRecord() == null) {
            throw new PayloadException("Payload with Record required", payload);
        }

        int matched = 0;
        for (String metaKey: metaKeys) {
            String searchValue = payload.getRecord().getMeta(metaKey);
            if (searchValue == null) {
                Logging.logProcess(
                    "RelationResolver",
                    "Unable to get search value from meta with key '" + metaKey
                    + "'",
                    Logging.LogLevel.TRACE, payload);
                continue;
            }
            DocumentResponse docResponse = getHits(payload, searchValue);
            if (docResponse.getHitCount() == 0) {
                log.trace(
                    "No hits found for search value '" + searchValue + "'");
                Logging.logProcess(
                    "RelationResolver",
                    "Unable to find any documents matching search value '"
                    + searchValue + "'",
                    Logging.LogLevel.TRACE, payload);
                continue;
            }
            matched += docResponse.getHitCount();
            assignRelatives(payload, docResponse);
        }
        if (matched == 0) {
            nonmatching(payload);
            Logging.logProcess(
                "RelationResolver", "Unable to resolve anything",
                Logging.LogLevel.DEBUG, payload);
            return !discardNonmatched;
        }
        return true;
    }

    protected DocumentResponse getHits(Payload payload, String searchValue)
                                                       throws PayloadException {
        Request request = new Request();
        String query =
            "".equals(searchField) ? searchValue : 
            searchField + ":\"" + searchValue + "\"";
        request.put(DocumentKeys.SEARCH_QUERY, query);
        request.put(DocumentKeys.SEARCH_MAX_RECORDS, maxHits);
        request.put(DocumentKeys.SEARCH_RESULT_FIELDS, DocumentKeys.RECORD_ID);
        ResponseCollection responses;
        try {
            log.debug("Searching with query '" + query + "'");
            responses = searcher.search(request);
        } catch (IOException e) {
            nonmatching(payload);
            throw new PayloadException(
                "Exception while searching with query '" + query + "'",
                e, payload);
        }
        DocumentResponse docResponse = null;
        for (Response response: responses) {
            if (response instanceof DocumentResponse) {
                docResponse = (DocumentResponse)response;
            }
        }
        if (docResponse == null) {
            nonmatching(payload);
            throw new PayloadException(
                "Did not receive a DocumentResponse when searching for query '"
                + query+ "'", payload);
        }
        return docResponse;
    }

    private void nonmatching(Payload payload) {
        if (nonmatchedFolder == null) {
            log.debug(
                payload + " not stored as no non-matching folder is defined");
            return;
        }
        File filename =
            new File(nonmatchedFolder, RecordUtil.getFileName(payload));
        log.trace("Storing non-matched " + payload.getRecord()
                  + " to '" + filename + "'");
        try {
            Files.saveString(payload.getRecord().getContentAsUTF8(), filename);
        } catch (IOException e) {
            log.warn("Unable to dump the content of non-matched "
                     + payload.getRecord() + " to file '" + filename + "'", e);
        }
    }

    private void assignRelatives(Payload payload, DocumentResponse docResponse) {
        Record record = payload.getRecord();
        if (assignAsParents) {
            List<String> parents =
                record.getParentIds() == null ?
                new ArrayList<String>(docResponse.getRecords().size()) :
                record.getParentIds();
            for (DocumentResponse.Record hitRecord: docResponse.getRecords()) {
                if (log.isTraceEnabled()) {
                    log.trace("Assigning parent ID '" + hitRecord.getId()
                              + "' to '" + record);
                }
                parents.add(hitRecord.getId());
            }
            record.setParentIds(parents);
            Logging.logProcess(
                "RelationResolver",
                "Assigned " + docResponse.getRecords().size() + " parents. "
                + "Parents are now " + Strings.join(parents, ", "),
                Logging.LogLevel.DEBUG, payload);
        }
        if (assignAsChildren) {
            List<String> children =
                record.getChildIds() == null ?
                new ArrayList<String>(docResponse.getRecords().size()) :
                record.getChildIds();
            for (DocumentResponse.Record hitRecord: docResponse.getRecords()) {
                if (log.isTraceEnabled()) {
                    log.trace("Assigning child ID '" + hitRecord.getId()
                              + "' to '" + record);
                }
                children.add(hitRecord.getId());
            }
            record.setChildIds(children);
            Logging.logProcess(
                "RelationResolver",
                "Assigned '" + docResponse.getRecords().size() + "' children. "
                + "children are now " + Strings.join(children, ", "),
                Logging.LogLevel.DEBUG, payload);
        }
    }

}