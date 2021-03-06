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
package dk.statsbiblioteket.summa.support.harmonise.hub;

import dk.statsbiblioteket.summa.common.configuration.Configurable;
import dk.statsbiblioteket.summa.common.configuration.Configuration;
import dk.statsbiblioteket.summa.common.util.ManyToManyMapper;
import dk.statsbiblioteket.util.Strings;
import dk.statsbiblioteket.util.qa.QAInfo;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.solr.client.solrj.response.FacetField;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.params.SolrParams;

import java.util.*;

/**
 * Adjusts tags in facets according to provided rules. This adjuster operates on Solr responses.
 */
@QAInfo(level = QAInfo.Level.NORMAL,
        state = QAInfo.State.IN_DEVELOPMENT,
        author = "te")
// TODO: Extend the tag format so that it handles escapes
// TODO: Implement discarding tags
public class HubTagAdjuster implements Configurable {
    private static Log log = LogFactory.getLog(HubTagAdjuster.class);

    /**
     * The name of the facets to adjust tags for. Specify with a list of facet
     * names delimited by comma or a plain String list.
     * </p><p>
     * Mandatory.
     */
    public static final String CONF_FACET_NAME = "tagadjuster.facet.name";

    /**
     * The map of tag transformations. The format supports many-to-many
     * transformations the following way where the number to the left of
     * the colon is the number of source tagnames and the number to the
     * right is the number of destination tag names:<br/>
     * {@code 1:1} Direct rename of the tag.<br/>
     * {@code 1:n} n new tags are created, each with the tag count from the
     * source. The source tag is removed.<br/>
     * {@code n:1} 1 new tag is created where the tag count is the sum of the
     * tag count for the sources.<br/>
     * {@code n:n} n new tag are created where the tag count for each is the sum
     * of the tag count for the sources.<br/>
     * If a tag is renamed to an existing tag, their tag counts are merged.
     * Rules such as {@code a-b, b-a} are handled gracefully.
     * </p><p>
     * The format of the map is<br/>
     * map: rule|rule,map<br/>
     * rule: source-destination<br/>
     * source: tagname|tagname;source<br/>
     * destination: tagname|tagname;destination<br/>
     * Blanks are trimmed from the start and the end of tagnames.
     * </p><p>
     * Example: {@code eng - english, dan;ger;hun - misc}.
     * </p><p>
     * Mandatory.
     */
    public static final String CONF_TAG_MAP = "tagadjuster.tags";

    /**
     * Create a single fieldName -> tagAdjuster structure from the given tagAdjusters.
     * @param tagAdjusters a list of adjusters.
     * @return a single mapping from field name to tag maps.
     * @throws IllegalStateException if two tagAdjusters containg the same field name.
     */
    public static Map<String, ManyToManyMapper> merge(List<HubTagAdjuster> tagAdjusters) {
        Map<String, ManyToManyMapper>  merged = new HashMap<>(tagAdjusters.size() * 2);
        for (HubTagAdjuster tagAdjuster: tagAdjusters) {
            for (String field: tagAdjuster.getFacetNames()) {
                if (merged.containsKey(field)) {
                    throw new IllegalStateException(
                            "Unable to add field '" + field + "' to merged tag maps as the field already exists");
                }
                merged.put(field, tagAdjuster.map);
            }
        }
        return merged;
    }

    /**
     * How to merge the counts for the tags when multiple source tags are mapped
     * to one destination tag (and by implication n:n).
     */
    public static enum MERGE_MODE {min, max, sum}

    /**
     * The way to merge counts for tags when more than one source tag maps to a
     * destination tag.
     * </p><p>
     * Optional. Default is sum. Valid values are min, max and sum.
     */
    public static final String CONF_MERGE_MODE = "tagadjuster.merge.mode";
    public static final MERGE_MODE DEFAULT_MERGE_MODE = MERGE_MODE.sum;

    private final Set<String> facetNames;
    private final MERGE_MODE mergeMode;
    private final ManyToManyMapper map;
    private String id; // An id used for timing feedback


    public HubTagAdjuster(Configuration conf) {
        facetNames = new HashSet<>(conf.getStrings(CONF_FACET_NAME));
        mergeMode = MERGE_MODE.valueOf(conf.getString(CONF_MERGE_MODE, DEFAULT_MERGE_MODE.toString()));
        map = new ManyToManyMapper(conf.getStrings(CONF_TAG_MAP));
        log.info("Created " + this);
    }

    public Set<String> getFacetNames() {
        return facetNames;
    }

    /**
     * Adjusts tags in the facets matching this adjuster.
     * @param request  the quest that resulted in the given response.
     * @param response facets mapped by this adjuster will be mapped.
     */
    @SuppressWarnings("UnusedParameters")
    public void adjust(SolrParams request, QueryResponse response) {
        List<FacetField> facetFields = response.getFacetFields();
        if (facetFields == null) {
            return;
        }
        for (FacetField facetField: facetFields) {
            expandTags(facetField);
        }
    }

    /**
     * @param facetField if this adjuster matches the facet field name, the contained tags will be mapped.
     */
    private void expandTags(FacetField facetField) {
        // Do we have a mapping at all?
        if (!facetNames.contains(facetField.getName())) {
            return;
        }

        Map<String, Long> tags = expandTags(facetField.getValues());
        facetField.getValues().clear();
        for (Map.Entry<String, Long> tag: tags.entrySet()) {
            facetField.add(tag.getKey(), tag.getValue());
        }
    }

    /**
     * Map and potential expand the tags.
     * @param tags the original tags to expand.
     * @return a temporary structure with the expanded tags.
     */
    private Map<String, Long> expandTags(List<FacetField.Count> tags) {
        Map<String, Long> expandedTags = new LinkedHashMap<>((int) (tags.size() * 1.5));
        for (FacetField.Count tag: tags) {
            Set<String> ets = map.getReverseSet(tag.getName());
            if (ets == null) {
                mergeTag(expandedTags, tag.getName(), tag.getCount());
            } else {
                for (String et: ets) {
                    mergeTag(expandedTags, et, tag.getCount());
                }
            }
        }
        return expandedTags;
    }

    /**
     * Merge the count into the given tags structure. If the tag already exists {@link #mergeMode} defines how the
     * counts are updated.
     * @param tags  existing tags.
     * @param name  the name of the new tag.
     * @param count the count for the new tag.
     */
    // TODO: Consider how and if to handle reliability
    private void mergeTag(Map<String, Long> tags, String name, long count) {
        if (!tags.containsKey(name)) {
            tags.put(name, count);
            return;
        }
        switch (mergeMode) {
            case min:
                tags.put(name, Math.min(tags.get(name), count));
                break;
            case max:
                tags.put(name, Math.max(tags.get(name), count));
                break;
            case sum:
                tags.put(name, tags.get(name) + count);
                break;
            default:
                throw new IllegalArgumentException("The mergeMode '" + mergeMode + "' is unknown");
        }
    }

    public void setID(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public ManyToManyMapper getMap() {
        return map;
    }

    @Override
    public String toString() {
        return "HubTagAdjuster(id='" + id + "', facetNames=[" + Strings.join(facetNames) + "], mergeMode=" + mergeMode
               + ", tagMap=" + map + ")";
    }
}
