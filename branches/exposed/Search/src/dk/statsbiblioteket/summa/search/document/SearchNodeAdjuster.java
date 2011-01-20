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
package dk.statsbiblioteket.summa.search.document;

import dk.statsbiblioteket.summa.common.configuration.Configuration;
import dk.statsbiblioteket.summa.common.configuration.SubConfigurationsNotSupportedException;
import dk.statsbiblioteket.summa.search.SearchNode;
import dk.statsbiblioteket.summa.search.SearchNodeFactory;
import dk.statsbiblioteket.summa.search.api.Request;
import dk.statsbiblioteket.summa.search.api.ResponseCollection;
import dk.statsbiblioteket.util.qa.QAInfo;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.logging.Log;

import java.rmi.RemoteException;

/**
 * Pipes requests and responses to and from a SearchNode through a
 * {@link SearchAdjuster}. The property {@link #CONF_INNER_SEARCHNODE} must
 * be specified and appropriate properties from {@link SearchAdjuster} should
 * be specified.
 * </p><p>
 * Note that rewriting of warmup queries is not performed as there are no
 * standard for the nature of these queries.
 */
@QAInfo(level = QAInfo.Level.NORMAL,
        state = QAInfo.State.IN_DEVELOPMENT,
        author = "te")
public class SearchNodeAdjuster implements SearchNode {
    private static Log log = LogFactory.getLog(SearchNodeAdjuster.class);

    /**
     * A sub-configuration with the setup for the SearchNode that is to be
     * created and used for all calls. The configuration must contain the
     * property {@link SearchNodeFactory#CONF_NODE_CLASS} as
     * {@link SearchNodeFactory} is used for creating the single inner node.
     * </p><p>
     * Mandatory.
     */
    public static final String CONF_INNER_SEARCHNODE =
        "adjuster.inner.searchnode";

    private final SearchNode inner;
    private final SearchAdjuster adjuster;

    public SearchNodeAdjuster(Configuration conf) {
        if (!conf.valueExists(CONF_INNER_SEARCHNODE)) {
            throw new ConfigurationException(
                "No inner search node defined. A proper sub-configuration must "
                + "exist for key " + CONF_INNER_SEARCHNODE);
        }
        try {
            inner = SearchNodeFactory.createSearchNode(
                conf.getSubConfiguration(CONF_INNER_SEARCHNODE));
        } catch (RemoteException e) {
            throw new ConfigurationException(
                "Unable to create inner search node, although a value were "
                + "present for key " + CONF_INNER_SEARCHNODE);
        } catch (SubConfigurationsNotSupportedException e) {
            throw new ConfigurationException(
                "A configuration with support for sub configurations must be "
                + "provided for the adjuster and must contain a sub "
                + "configuration with key " + CONF_INNER_SEARCHNODE);
        }
        adjuster = new SearchAdjuster(conf);
        log.debug("Created SearchNodeAdjuster with inner SearchNode " + inner);
    }

    @Override
    public void search(Request request, ResponseCollection responses)
        throws RemoteException {
        log.debug(
            "Rewriting request, performing search and adjusting responses");
        Request adjusted = adjuster.rewrite(request);
        inner.search(adjusted, responses);
        adjuster.adjust(adjusted, responses);
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
        return "SearchNodeAdjuster(" + adjuster.getId()
               + " for " + super.toString() + ")";
    }
}
