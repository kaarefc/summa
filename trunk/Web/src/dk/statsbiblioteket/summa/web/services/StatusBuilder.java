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
package dk.statsbiblioteket.summa.web.services;

import dk.statsbiblioteket.summa.common.util.Pair;
import dk.statsbiblioteket.util.qa.QAInfo;
import dk.statsbiblioteket.util.xml.DOM;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.IOException;
import java.text.DateFormat;
import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Status builder.
 *
 * @author Mikkel Kamstrup Erlandsen
 * @author Henrik Kirk <mailto:hbk@statsbiblioteket.dk>
 * @since Nov 25, 2009
 */
@QAInfo(level = QAInfo.Level.NORMAL,
        state = QAInfo.State.QA_NEEDED,
        author = "mke",
        reviewers = "hbk")
public class StatusBuilder {
    /**
     * Static Properties class.
     */
    static class Properties {
        Node group;

        /**
         * Constructor for properties class.
         *
         * @param group The Node group, if null NullPointerException is thrown.
         */
        Properties(Node group) {
            if (group == null) {
                throw new NullPointerException(
                                              "Properties based on null group");
            }
            this.group = group;
        }

        /**
         * Getter for a node child, specified by input 'name'.
         *
         * @param name The child name.
         * @return the node child, given the specified 'name'.
         */
        Node get(String name) {
            NodeList children = group.getChildNodes();

            if (children == null) {
                return null;
            }

            int numChildren = children.getLength();
            for (int i = 0; i < numChildren; i++) {
                Node child = children.item(i);
                if (!child.hasAttributes()) {
                    continue;
                }

                Node prop = child.getAttributes().getNamedItem("name");

                if (prop == null) {
                    continue;
                }

                if (prop.getTextContent().equals(name)) {
                    return child;
                }
            }
            return null;
        }

        /**
         * Get the child with names text.
         * @param name The name of the child, which text to return.
         * @return text for a by 'name' specified child.
         */
        String getText(String name) {
            Node prop = get(name);
            if (prop == null) {
                return "Undefined";
            }

            Node child = prop.getFirstChild();
            if (child == null) {
                return "Undefined value for property '" + name + "'";
            }

            return child.getTextContent().trim();
        }
    }

    private Node statusDom;
    private Properties searcher;
    private Properties storage;
    private Properties suggest;
    private List<Pair<String,String>> queryCount;
    private String searcherStatus;
    private String storageStatus;
    private String suggestStatus;
    private String lastUpdate;
    private String now;
    DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");
    NumberFormat numberFormat = NumberFormat.getIntegerInstance();

    /**
     * Constructor for StatusBuilder.
     *
     * @param statusDom The DOM used to build the status web content.
     */
    public StatusBuilder(Node statusDom) {
        this.statusDom = statusDom;

        searcher = new Properties(
                DOM.selectNode(statusDom, "/status/group[@name='searcher']"));
        storage = new Properties(
                DOM.selectNode(statusDom, "/status/group[@name='storage']"));
        suggest = new Properties(
                DOM.selectNode(statusDom, "/status/group[@name='suggest']"));
        queryCount = sortQueries(DOM.selectNodeList(
                      statusDom, "/status/group[@name='queryCount']/property"));

        searcherStatus = searcher.getText("status");
        storageStatus = storage.getText("status");
        suggestStatus = suggest.getText("status");

        // FIXME: In the future we should probably use the moment
        // of index update instead
        lastUpdate = storage.getText("lastUpdate");
        now = dateFormat.format(new Date());

        // Minimum 6 digits and don't group numbers with . or ,
        numberFormat.setGroupingUsed(false);
        numberFormat.setMinimumIntegerDigits(6);
    }

    /**
     * Parse the queryCount group into a sorted list of Pairs (count, query).
     *
     * @param nodes Items to parse and sort.
     * @return a sorted collection with Pairs (count, query).
     */
    private List<Pair<String, String>> sortQueries(NodeList nodes) {
        List<Pair<String, String>> l = new LinkedList<Pair<String, String>>();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            l.add(
                new Pair<String, String>(
                    node.getAttributes().getNamedItem("name").getTextContent(),
                    node.getFirstChild().getTextContent().trim()
                )
            );
        }

        Collections.sort(l);
        Collections.reverse(l);

        return l;
    }

    /**
     * Return true if searcher, storage and suggest is 'OK'.
     * @return true if searcher, storage and suggest is 'OK', false otherwise.
     */
    public boolean allGood() {
        return "OK".equals(searcherStatus) && "OK".equals(storageStatus)
            && "OK".equals(suggestStatus);
    }

    /**
     * Get last update.
     *         2010-03-25T11:15:20.426
     * Fixme: code isn't nice or fast.
     *
     * @param granularity The Granularity of the dateString. Should be {year,
     * month, day, hour, minute, second}.
     * @param date The date object, which should be granulariated.
     * @return return last update time with a given granularity.
     */
    public String getLastUpdate(String granularity, Date date) {
        String output = "";

        Calendar cal = new GregorianCalendar();
        cal.setTime(date);

        // Year
        output += cal.get(Calendar.YEAR);
        if (granularity.equals("year")) {
            return output;
        }

        // Month
        output += "-" + cal.get(Calendar.MONTH);
        if (granularity.equals("month")) {
            return output;
        }

        // Day
        output += "-" + cal.get(Calendar.DAY_OF_MONTH);
        if (granularity.equals("day")) {
            return output;
        }

        // Hour
        output += "T" + cal.get(Calendar.HOUR_OF_DAY);
        if (granularity.equals("hour")) {
            return output;
        }

        // Min
        output += ":" + cal.get(Calendar.MINUTE);
        if (granularity.equals("minute")) {
            return output;
        }

        // Sec
        output += ":" + cal.get(Calendar.SECOND);
        if (granularity.equals("second")) {
            return output;
        }

        // Day
        output += ";" + cal.get(Calendar.MILLISECOND);
        return output;
    }

    /**
     * Get last update.
     *
     * @return return last update time.
     */
    public String getLastUpdate() {
        return lastUpdate;
    }

    /**
     * Get last update, defined as a timestamp.
     *
     * @return last update time as a date object.
     */
    public Date getLastUpdateStamp() {
        try {
            return dateFormat.parse(lastUpdate);
        } catch (ParseException e) {
            return new Date();
        }
    }

    /**
     * Get creation time for this status builder.
     *
     * @return creation time for this status builder object as date object.
     */
    public Date getNowStamp() {
        try {
            return dateFormat.parse(now);
        } catch (ParseException e) {
            return new Date();
        }
    }

    /**
     * Creation time for this object.
     *
     * @return string in 'yyyy-MM-dd'T'HH:mm:ss.SSS' for the creation time of
     * this object.
     */
    public String getNow() {
        return now;
    }

    /**
     * ToString for this object, return html for the content of the status
     * web page.
     *
     * @return html content for a status web page.
     */
    public String toString() {
        try {
            return toString(new StringBuilder()).toString();
        } catch (IOException e) {
            throw new RuntimeException(
              "IOException while writing to memory. This should never happen!");
        }
    }

    /**
     * Given a Appendable object, this method creates content for a web page
     * used to present the status.
     *
     * @param buf buffer for appending the content of the web page.
     * @return html for presenting a status message on a web page.
     * @throws IOException if errors are encountered while appending to buffer.
     */
    public Appendable toString(Appendable buf) throws IOException {
        buf.append("<p>\n");
        buf.append("Last update: ").append(lastUpdate).append("<br/>");
        buf.append("Report generated: ").append(now).append("<br/>");
        buf.append("</p>\n");

        buf.append("<p>\n");
        buf.append("<b>Searcher:</b> <i>").append(searcherStatus)
                .append("</i><br/>");
        buf.append("Number of documents: ")
            .append(searcher.getText("numDocs"))
            .append("<br/>");
        buf.append("Response time: ")
            .append(searcher.getText("responseTime"))
            .append("ms<br/>");
        buf.append("Raw search time: ")
            .append(searcher.getText("searchTime"))
            .append("ms<br/>");
        buf.append("</p>\n");

        buf.append("<p>\n");
        buf.append("<b>Storage:</b> <i>").append(storageStatus)
                .append("</i><br/>");
        buf.append("Response time: ")
            .append(storage.getText("responseTime"))
            .append("ms<br/>");
        buf.append("</p>\n");

        buf.append("<p>\n");
        buf.append("<b>Suggest:</b> <i>").append(suggestStatus)
                .append("</i><br/>");
        buf.append("Response time: ")
            .append(suggest.getText("responseTime"))
            .append("ms<br/>");
        buf.append("</p>\n");

        buf.append("<p>\n");
        buf.append("<b>Popular queries the last 24 hours</b>");
        buf.append("<i>(number of queries - query string)</i>:<br/>");
        if (queryCount.size() > 0) {
            buf.append("<ul>");
            for (Pair<String, String> prop : queryCount) {
                buf.append("<li>")
                   .append(Integer.toString(Integer.parseInt(prop.getKey())))
                   .append(" - ")
                   .append("<tt>'")
                   .append(prop.getValue())
                   .append("'</tt>")
                   .append("</li>\n");
            }
            buf.append("</ul>\n");
        }
        buf.append("</p>\n");

        Node holdings = DOM.selectNode(statusDom,
          "/status/group[@name='storage']/property[@name='holdings']/holdings");

        buf.append("<b>Storage holdings:</b>");
        if (holdings != null) {
            NodeList bases = holdings.getChildNodes();
            if (bases.getLength() > 0) {
                buf.append("<ul>\n");
                for (int i = 0; i < bases.getLength(); i++) {
                    if (bases.item(i).getNodeType() != Node.ELEMENT_NODE) {
                        continue;
                    }
                    NamedNodeMap base = bases.item(i).getAttributes();
                    buf.append("<li><tt>")
                       .append(base.getNamedItem("name").getTextContent())
                       .append("</tt><br/>")
                       .append("Live: ")
                       .append(base.getNamedItem("live").getTextContent())
                       .append(", total: ")
                       .append(base.getNamedItem("total").getTextContent())
                       .append(". Updated: ")
                       .append(base.getNamedItem("mtime").getTextContent())
                       .append("</li>\n");
                }
                buf.append("</ul>\n");
            }
        } else {
            buf.append(" Not available");
        }
        return buf;
    }
}
