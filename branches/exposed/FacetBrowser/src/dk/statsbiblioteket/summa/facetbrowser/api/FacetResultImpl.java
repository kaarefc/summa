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
/*
 * The State and University Library of Denmark
 * CVS:  $Id: FacetResultImpl.java,v 1.10 2007/10/05 10:20:22 te Exp $
 */
package dk.statsbiblioteket.summa.facetbrowser.api;

import dk.statsbiblioteket.summa.common.util.CollatorFactory;
import dk.statsbiblioteket.summa.facetbrowser.FacetStructure;
import dk.statsbiblioteket.summa.facetbrowser.Structure;
import dk.statsbiblioteket.summa.search.api.Response;
import dk.statsbiblioteket.summa.common.util.FlexiblePair;
import dk.statsbiblioteket.summa.common.util.Pair;
import dk.statsbiblioteket.summa.search.api.ResponseImpl;
import dk.statsbiblioteket.util.qa.QAInfo;
import dk.statsbiblioteket.util.xml.XMLUtil;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.StringWriter;
import com.ibm.icu.text.Collator;
import java.util.*;

/**
 * Base implementation of a facet structure, where the tags are generic.
 * Resolving tags to queries and representations are delegated to implementing
 * classes. The same goes for sort-order of the tags.
 */
@QAInfo(level = QAInfo.Level.NORMAL,
        state = QAInfo.State.IN_DEVELOPMENT,
        author = "te")
public abstract class FacetResultImpl<T extends Comparable<T>>
    extends ResponseImpl implements FacetResult<T> {
    private static final transient Log log =
            LogFactory.getLog(FacetResultImpl.class);
    private static final long serialVersionUID = 7879716849L;

    private int DEFAULTFACETCAPACITY = 20;
    private static final int DEFAULT_MAXTAGS = 100;

    /**
     * If false, tags containing the empty String will not be part of the
     * generated XML. Note that this might result in a reduction of the
     * number of returned tags by 1.
     * </p><p>
     * Default: false.
     */
    public boolean emptyTagsValid = false;

    /**
     * Pseudo-code: Map<FacetName, FlexiblePair<Tag, TagCount>>.
     * We use a linked map so that the order of the Facets will be
     * significant.
     */
    protected LinkedHashMap<String, List<FlexiblePair<T, Integer>>> map;
    protected HashMap<String, Integer> maxTags;
    protected HashMap<String, Integer> facetIDs;

    /**
     * @param maxTags  a map from Facet-name to max tags for the facet.
     * @param facetIDs a map from Facet-name to facetID.
     */
    public FacetResultImpl(HashMap<String, Integer> maxTags,
                           HashMap<String, Integer> facetIDs) {
        map = new LinkedHashMap<String, List<FlexiblePair<T, Integer>>>(
                DEFAULTFACETCAPACITY);
        this.maxTags = maxTags;
        this.facetIDs = facetIDs;
    }

    /**
     * It is advisable to call reduce before calling toXML, to ensure that
     * all elements are trimmed and sorted.
     * @return an XML representation of the facet browser structure.
     */
    @Override
    // TODO: Switch to XMLOutputStream
    public synchronized String toXML() {
        log.trace("Entering toXML");
        StringWriter sw = new StringWriter(10000);

        sw.write("<facetmodel>\n");
        for (Map.Entry<String, List<FlexiblePair<T, Integer>>> facet:
                map.entrySet()) {
            if (facet.getValue().size() > 0) {
                sw.write("  <facet name=\"");
                sw.write(XMLUtil.encode(facet.getKey()));
                // TODO: Preserve scoring
                sw.write("\">\n");

    //            sw.write(facet.getCustomString());
                int tagCount = 0;

                Integer maxTags = this.maxTags.get(facet.getKey());
                if (maxTags == null) {
                    maxTags = DEFAULT_MAXTAGS;
                }
//                        structure.getFacets().get(facet.getKey()).getMaxTags();
                for (FlexiblePair<T, Integer> tag: facet.getValue()) {
                    String tagString = getTagString(
                        facet.getKey(), tag.getKey());
                    if (!emptyTagsValid && "".equals(tagString)) {
                        log.trace("Skipping empty tag from " + facet.getKey()
                                  + " with tag count " + tag.getValue());

                        continue;
                    }
                    if (tagCount++ < maxTags) {
                        sw.write("    <tag name=\"");
                        sw.write(XMLUtil.encode(tagString));
        /*                if (!Element.NOSCORE.equals(tag.getScore())) {
                            sw.write("\" score=\"");
                            sw.write(Float.toString(tag.getScore()));
                        }*/
                        sw.write("\" addedobjects=\"");
                        sw.write(Integer.toString(tag.getValue()));
                        sw.write("\">\n");
                        //noinspection DuplicateStringLiteralInspection
                        sw.write("    <query>"
                                 + getQueryString(facet.getKey(), tag.getKey())
                                 + "</query>\n");
        /*                for (T object: tag.getObjects()) {
                            sw.write("      <object>");
                            sw.write(object.toString());
                            sw.write("</object>\n");
                        }*/
                        sw.write("    </tag>\n");
                    }
                }
                sw.write("  </facet>\n");
            } else {
                log.trace("Skipped \"" + facet.getKey() + "\" as it did not "
                          + "contain any tags");
            }
        }
        sw.write("</facetmodel>\n");
        return sw.toString();
    }

    /**
     * This method is a fallback and sorts all facets the same way. It is highly
     * recommended to use
     * {@link #reduce(dk.statsbiblioteket.summa.facetbrowser.Structure)} if
     * the original request structure is available.
     */
    @Override
    public synchronized void reduce(TagSortOrder tagSortOrder) {
        LinkedHashMap<String, List<FlexiblePair<T, Integer>>> newMap =
                new LinkedHashMap<String,
                        List<FlexiblePair<T, Integer>>>(map.size());
        for (Map.Entry<String, List<FlexiblePair<T, Integer>>> entry:
                map.entrySet()) {
            Integer maxTags = this.maxTags.get(entry.getKey());
            if (maxTags == null) {
                maxTags = DEFAULT_MAXTAGS;
            }
//                    structure.getFacets().get(entry.getKey()).getMaxTags();
            if (entry.getValue().size() <= maxTags) {
                newMap.put(entry.getKey(), entry.getValue());
            } else {
                newMap.put(entry.getKey(),
                           new ArrayList<FlexiblePair<T, Integer>>(
                                   entry.getValue().subList(0, maxTags)));
            }
        }
        map = newMap;
        sort(tagSortOrder);
    }

    /**
     * Reduces the number of tags in the facets, as per the given request
     * structure.
     * @param structure a request structure describing the facet setup.
     */
    public synchronized void reduce(Structure structure) {
        LinkedHashMap<String, List<FlexiblePair<T, Integer>>> newMap =
                new LinkedHashMap<String,
                        List<FlexiblePair<T, Integer>>>(map.size());
        for (Map.Entry<String, List<FlexiblePair<T, Integer>>> facet:
                map.entrySet()) {
            String facetName = facet.getKey();
            List<FlexiblePair<T, Integer>> tags = facet.getValue();

            Integer maxTags = structure.getMaxTags().get(facetName);
            if (maxTags == null) { // Fallback 1
                maxTags = this.maxTags.get(facetName);
            }
            if (maxTags == null) { // Fallback 2
                maxTags = DEFAULT_MAXTAGS;
            }
            FacetStructure fc = structure.getFacet(facetName);
            if (fc != null) {
                if (FacetStructure.SORT_POPULARITY.equals(fc.getSortType())) {
                    sortPopularity(facet);
                } else if (FacetStructure.SORT_ALPHA.equals(fc.getSortType())) {
                    sortAlpha(facet, fc.getLocale());
                }
            }
//                    structure.getFacets().get(entry.getKey()).getMaxTags();
            if (!emptyTagsValid) {
                List<FlexiblePair<T, Integer>> tagList = facet.getValue();
                for (int i = 0 ; i < tagList.size() ; i++) {
                    if ("".equals(getTagString(
                        facetName, tagList.get(i).getKey()))) {
                        log.trace("Removing empty tag from " + facetName
                                  + " with tag count "
                                  + tagList.get(i).getValue());
                        tagList.remove(i);
                        break;
                    }
                }
            }
            if (facet.getValue().size() <= maxTags) {
                newMap.put(facet.getKey(), facet.getValue());
            } else {
                newMap.put(facet.getKey(),
                           new ArrayList<FlexiblePair<T, Integer>>(
                                   facet.getValue().subList(0, maxTags)));
            }
        }
        map = newMap;
    }

    private void sortAlpha(
        final Map.Entry<String, List<FlexiblePair<T, Integer>>> facet,
        final String locale) {
        final Collator collator = locale == null ? null :
                            CollatorFactory.createCollator(new Locale(locale));

        Collections.sort(facet.getValue(),
            new Comparator<FlexiblePair<T, Integer>>() {
                @Override
                public int compare(FlexiblePair<T, Integer> o1,
                                   FlexiblePair<T, Integer> o2) {
                    String key1 = getTagString(facet.getKey(), o1.getKey());
                    String key2 = getTagString(facet.getKey(), o2.getKey());
                    return collator == null ? key1.compareTo(key2) :
                           collator.compare(key1, key2);
                }
            });
    }

    private void sortPopularity(
        final Map.Entry<String, List<FlexiblePair<T, Integer>>> facet) {
        Collections.sort(facet.getValue(),
            new Comparator<FlexiblePair<T, Integer>>() {
                @Override
                public int compare(FlexiblePair<T, Integer> o1,
                                   FlexiblePair<T, Integer> o2) {
                    // Falling
                    int val = o2.getValue().compareTo(o1.getValue());
                    return val != 0 ? val :
                           getTagString(facet.getKey(), o1.getKey()).
                               compareTo(getTagString(facet.getKey(),
                                                      o1.getKey()));
                }
            });
    }

    /**
     * Used by the default merge.
     * @return the internal map.
     */
    public Map<String, List<FlexiblePair<T, Integer>>> getMap() {
        return map;
    }

    @Override
    @SuppressWarnings({"unchecked"})
    public void merge(Response otherResponse) throws ClassCastException {
        if (!(otherResponse instanceof FacetResult)) {
            //noinspection ProhibitedExceptionThrown
            throw new ClassCastException(String.format(
                    "Expected a FacetResult, but go '%s'",
                    otherResponse.getClass().getName()));
        }
        super.merge(otherResponse);
        FacetResult other = (FacetResult)otherResponse;
        if (other == null) {
            log.warn("Attempted to merge with null");
        }
        String typeProblem = "The FacetResultImpl<T> default merger can only"
                             + " handle FacetResultImpl<T> as input";
        if (!(other instanceof FacetResultImpl)) {
            throw new IllegalArgumentException(typeProblem);
        }
        Map<String, List<FlexiblePair<T, Integer>>> otherMap;
        try  {
            //noinspection unchecked
            otherMap = ((FacetResultImpl<T>)other).getMap();
        } catch (ClassCastException e) {
            throw new IllegalArgumentException(typeProblem, e);
        }

        // other is source and this is destination
        //noinspection unchecked
        mergeMaxTags((FacetResultImpl<T>) other);
        for (Map.Entry<String, List<FlexiblePair<T, Integer>>> sEntry:
                otherMap.entrySet()) {
            List<FlexiblePair<T, Integer>> dList = map.get(sEntry.getKey());
            if (dList == null) { // Just add the taglist
                map.put(sEntry.getKey(), sEntry.getValue());
            } else { // Merge the tags
                List<FlexiblePair<T, Integer>> sList = sEntry.getValue();
                for (FlexiblePair<T, Integer> sPair: sList) {
                    boolean found = false;
                    for (FlexiblePair<T, Integer> dPair: dList) {
                        if (sPair.getKey().equals(dPair.getKey())) { // Merge
                            dPair.setValue(sPair.getValue() + dPair.getValue());
                            found = true;
                            break;
                        }
                    }
                    if (!found) { // Add non-existing
                        dList.add(sPair);
                    }
                }
            }
        }
    }

    private void mergeMaxTags(FacetResultImpl<T> otherResult) {
        log.trace("Merging maxTags");
        for (Map.Entry<String, Integer> maxTag: otherResult.maxTags.entrySet()){
            if (!maxTags.containsKey(maxTag.getKey())) {
                maxTags.put(maxTag.getKey(), maxTag.getValue());
            }
        }
    }

    /**
     * Shortcut for sortTags and sortFacets methods.
     * @param tagSortOrder The sort order for the tags.
     */
    protected void sort(TagSortOrder tagSortOrder) {
        sortTags(tagSortOrder);
        sortFacets();
    }

    protected void sortTags(TagSortOrder tagSortOrder) {
        for (Map.Entry<String, List<FlexiblePair<T, Integer>>> facet:
                map.entrySet()) {
            switch (tagSortOrder) {
                case tag:
                    Collections.sort(facet.getValue(),
                        new Comparator<FlexiblePair<T, Integer>>() {
                            @Override
                            public int compare(FlexiblePair<T, Integer> o1,
                                               FlexiblePair<T, Integer> o2) {
                                return compareTags(o1, o2);
                            }
                        });
                    break;
                case popularity:
                    Collections.sort(facet.getValue(),
                        new Comparator<FlexiblePair<T, Integer>>() {
                            @Override
                            public int compare(FlexiblePair<T, Integer> o1,
                                               FlexiblePair<T, Integer> o2) {
                                return o1.getValue().compareTo(o2.getValue());
                            }
                        });
                    break;
                default:
                    log.error("Unknown tag sort order in sortTags: " +
                              tagSortOrder);
            }
        }
    }

    public void sortFacets() {
        //final Structure s2 = structure;
        // construct list
        List<Pair<String, List<FlexiblePair<T, Integer>>>> ordered =
                new ArrayList<Pair<String, List<FlexiblePair<T, Integer>>>>(
                        map.size());
        for (Map.Entry<String, List<FlexiblePair<T, Integer>>> facet:
                map.entrySet()) {
            ordered.add(new Pair<String, List<FlexiblePair<T, Integer>>>(
                    facet.getKey(), facet.getValue()));
        }
        // Sort it
        Collections.sort(ordered,
            new Comparator<Pair<String, List<FlexiblePair<T, Integer>>>>() {
                @Override
                public int compare(Pair<String,
                                        List<FlexiblePair<T, Integer>>> o1,
                                   Pair<String,
                                        List<FlexiblePair<T, Integer>>> o2) {
               // TODO: This way of sorting does not work for different sources
                    Integer score1 = facetIDs.get(o1.getKey());
//                            s2.getFacet(o1.getKey()).getFacetID();
                    Integer score2 = facetIDs.get(o2.getKey());
//                            s2.getFacet(o2.getKey()).getFacetID();
                    if (score1 != null && score2 != null) {
                        return score1.compareTo(score2);
                    } else if (score1 != null) {
                        return -1;
                    } else if (score2 != null) {
                        return 1;
                    } else {
                        return o1.getKey().compareTo(o2.getKey());
                    }
                }
            });
        map.clear();
        for (Pair<String, List<FlexiblePair<T, Integer>>> entry: ordered) {
            map.put(entry.getKey(), entry.getValue());
        }
    }

    /**
     * This should be overridden when extending, if the tags order is not
     * natural.
     * @param o1 The first object to compare.
     * @param o2 The second object to compare.
     * @return -1, 0 or 1, depending on order.
     */
    protected int compareTags(FlexiblePair<T, Integer> o1,
                              FlexiblePair<T, Integer> o2) {
        return o1.compareTo(o2);
    }

    /**
     * This should be overridden when extending, if the tags does not resolve
     * naturally to Strings.
     * @param facet The facet that contains the tag.
     * @param tag The tag to convert to String.
     * @return a String-representation of the Tag.
     */
    @SuppressWarnings({"UnusedDeclaration"})
    protected String getTagString(String facet, T tag) {
        log.trace("Default-implementation of getTagString called with Tag "
                  + tag);
        return String.valueOf(tag);
    }
    /**
     * This should be overridden when extending, if the tags does not resolve
     * naturally to queries.
     * @param facet The facet that contains the tag.
     * @param tag The tag to convert to a query.
     * @return a query for the tag in the facet.
     */
    protected String getQueryString(String facet, T tag) {
        return facet + ":\""
               + XMLUtil.encode(queryEscapeTag(String.valueOf(tag))) + "\"";
    }

    /**
     * Assign the list of tags to the given Facet. Any existing Facet will be
     * overwritten.
     * @param facet The Facet to assign to.
     * @param tags a list of pars, where the first element is the tag and the
     *             second element is the tag-count.
     */
    public void assignTags(String facet, List<FlexiblePair<T, Integer>> tags) {
        map.put(facet, tags);
    }

    /**
     * Adds the list of tags to the given Facet. If the Facet does not exist, it
     * will be created. Each Tag will be added to the tags for the Facet.
     * If a Tag already exists in the list, the tagCounts will be added for
     * that Tag. This requires iteration through the tags, so consider using
     * {@link #assignTags} if the uniqueness of the Tags is known.<br />
     * Note that the SortOrder for the FlexiblePairs may be reset by this
     * method.
     * @param facet The Facet to assign to.
     * @param tags A list of pars, where the first element is the tag and the
     *             second element is the tag-count.
     */
    public void addTags(String facet, List<FlexiblePair<T, Integer>> tags) {
        if (map.containsKey(facet)) {
            for (FlexiblePair<T, Integer> tag: tags) {
                addTag(facet, tag.getKey(), tag.getValue());
            }
        } else {
            assignTags(facet, tags);
        }
    }

    /**
     * Add a given Tag to a given Facet. If the Tag already exists, the tagCount
     * is added to the existing tagCount. Note that this iterates through all
     * Tags in the given Facet, thus being somewhat inefficient. Consider using
     * {@link #assignTag} if the Tag is known to be unique within the Facet.
     * @param facet    The Facet to add the Tag to. If it does not exist, a new
     *                 Facet is created.
     * @param tag      The Tag to add to the Facet.
     * @param tagCount The tagCount for the Tag.
     */
    public void addTag(String facet, T tag, int tagCount) {
        List<FlexiblePair<T, Integer>> tags = map.get(facet);
        if (tags == null) {
            tags = new ArrayList<FlexiblePair<T, Integer>>(
                    DEFAULTFACETCAPACITY);
            map.put(facet, tags);
        }
        for (FlexiblePair<T, Integer> tPair: tags) {
            if (tPair.getValue().equals(tag)) {
                tPair.setValue(tPair.getValue() + tagCount);
                return;
            }
        }
        // TODO: Remove SortType?
        tags.add(new FlexiblePair<T, Integer>(tag, tagCount,
                                   FlexiblePair.SortType.SECONDARY_DESCENDING));
    }

    /**
     * Assigns a given Tag to a given Facet. There is no checking for duplicate
     * Tags, so ensuring consistency is up to the caller. Consider using
     * {@link #addTag} if it is unknown whether the tag is unique.
     * @param facet    The Facet to assign the Tag to. If it does not exist,
     *                 a new Facet is created.
     * @param tag      The Tag to assign to the Facet.
     * @param tagCount The tagCount for the Tag.
     */
    public void assignTag(String facet, T tag, int tagCount) {
        List<FlexiblePair<T, Integer>> tags = map.get(facet);
        if (tags == null) {
            tags = new ArrayList<FlexiblePair<T, Integer>>(
                    DEFAULTFACETCAPACITY);
            map.put(facet, tags);
        }
        // TODO: Remove SortType?
        tags.add(new FlexiblePair<T, Integer>(tag, tagCount,
                                   FlexiblePair.SortType.SECONDARY_DESCENDING));
    }

    public static String urlEntityEscape(String text) {
        return text.replace("&",  "&amp;").
                    replace("<",  "&lt;").
                    replace(">",  "&gt;").
                    replace("#",  "%23"). // Escaping for URL
                    replace("\"", "&quot;");
    }

    /**
     * Constructs a list of the Tags under the given Facet.
     * @param facet The facet to get Tags for.
     * @return all the Tags under the given Facet or null if the Facet does not
     *         exist.
     */
    public List<String> getTags(String facet) {
        if (!map.containsKey(facet)) {
            log.debug("getTags(" + facet + "): Could not locate facet");
            return null;
        }
        List<String> result = new ArrayList<String>(map.get(facet).size());
        for (FlexiblePair<T, Integer> pair: map.get(facet)) {
            result.add(getTagString(facet, pair.getKey()));
        }
        return result;
    }

    /**
     * Escape the tag for use in a query. Currently this means placing a
     * backslash in front of quotes.
     * @param cleanTag The tag to escape.
     * @return the escaped tag.
     */
    protected String queryEscapeTag(String cleanTag) {
        return cleanTag.replace("\"", "\\\"");
    }

    protected String adjust(Map<String, String> replacements, String s) {
        return replacements.containsKey(s) ? replacements.get(s) : s;
    }

    protected String[] adjust(Map<String, String> replacements, String[] s) {
        for (int i = 0 ; i < s.length ; i++) {
            s[i] = adjust(replacements, s[i]);
        }
        return s;
    }

    /**
     * Renames the facet. and the field-names according to the map.
     * @param replacements oldName -> newName for facets and fields.
     */
    public synchronized void renameFacetsAndFields(
                                             Map<String, String> replacements) {
        LinkedHashMap<String, List<FlexiblePair<T, Integer>>> newTags =
            new LinkedHashMap<String, List<FlexiblePair<T, Integer>>>(map.size());
        for (Map.Entry<String, List<FlexiblePair<T, Integer>>> entry:
            map.entrySet()) {
            newTags.put(adjust(replacements, entry.getKey()), entry.getValue());
        }
        map = newTags;

        maxTags = adjust(replacements, maxTags);
        facetIDs = adjust(replacements, facetIDs);
    }

    private HashMap<String, Integer> adjust(
        Map<String, String> replacements, HashMap<String, Integer> map) {
        HashMap<String, Integer> adjusted = new HashMap<String, Integer>(map.size());
        for (Map.Entry<String, Integer> entry: map.entrySet()) {
            adjusted.put(adjust(
                replacements, entry.getKey()), entry.getValue());
        }
        return adjusted;
    }

    public boolean isEmptyTagsValid() {
        return emptyTagsValid;
    }

    public void setEmptyTagsValid(boolean emptyTagsValid) {
        this.emptyTagsValid = emptyTagsValid;
    }
}

