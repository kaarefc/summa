/* $Id: StorageBase.java,v 1.9 2007/12/04 09:08:19 te Exp $
 * $Revision: 1.9 $
 * $Date: 2007/12/04 09:08:19 $
 * $Author: te $
 *
 * The Summa project.
 * Copyright (C) 2005-2007  The State and University Library
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
package dk.statsbiblioteket.summa.storage;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Arrays;
import java.io.IOException;

import dk.statsbiblioteket.summa.common.Record;
import dk.statsbiblioteket.summa.common.rpc.RemoteHelper;
import dk.statsbiblioteket.summa.common.configuration.Configuration;
import dk.statsbiblioteket.summa.storage.api.Storage;
import dk.statsbiblioteket.summa.storage.api.WritableStorage;
import dk.statsbiblioteket.summa.storage.api.ReadableStorage;
import dk.statsbiblioteket.summa.storage.api.rmi.RemoteStorage;
import dk.statsbiblioteket.util.qa.QAInfo;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * StorageBase is an abstract class to facilitate implementations of the
 * {@link Storage} interface.
 * <p/>
 * There is no choice of storage in StorageBase. This choice is made in the
 * subclasses.
 */
@QAInfo(level = QAInfo.Level.NORMAL,
        state = QAInfo.State.IN_DEVELOPMENT,
        author = "hal, te, mke")
public abstract class StorageBase extends UnicastRemoteObject
                                  implements RemoteStorage {
    private static Log log = LogFactory.getLog(StorageBase.class);

    public StorageBase(Configuration conf) throws IOException {
        super (getServicePort(conf));

        log.trace("Exporting Storage interface");
        RemoteHelper.exportRemoteInterface(this,
                                           conf.getInt(Storage.CONF_REGISTRY_PORT, 28000),
                                           "summa-storage");

        try {
            RemoteHelper.exportMBean(this);
        } catch (Exception e) {
            log.warn ("Failed to register MBean, going on without it. "
                      + "Error was", e);
        }
    }

    /**
     * Create the storage base with an empty configuration. This means that
     * default values will be used throughout.
     */
    public StorageBase() throws IOException {
        this(Configuration.newMemoryBased());
    }

    private static int getServicePort(Configuration configuration) {
        try {
            return configuration.getInt(Storage.CONF_SERVICE_PORT);
        } catch (NullPointerException e) {
            log.warn ("Service port not defined in "
                    + Storage.CONF_SERVICE_PORT + ". Falling back to "
                    + "anonymous port");
            return 0;
        }
    }

    /**
     * Default implementation that uses {@link #next(Long)} to create the list
     * of RecordAndNext. As this happens server-side, this should be fast
     * enough.
     */
    public List<Record> next(Long iteratorKey, int maxRecords) throws
                                                               RemoteException {
        List<Record> records = new ArrayList<Record>(maxRecords);
        int added = 0;
        while (added++ < maxRecords) {
            try {
                Record r = next(iteratorKey);
                records.add(r);
            } catch (NoSuchElementException e) {
                break;
            }

        }
        return records;
    }

    protected void updateRelations(Record record)
                                                            throws IOException {
        if (log.isTraceEnabled()) {
            log.trace("updateRelations("+record.getId()+")");
        }

        /* We collect a list of changes and submit them in one transaction */
        List<Record> flushQueue = new ArrayList<Record>(5);

        /* Make sure parent records are correct */
        if (record.getParentIds() != null) {
            List<Record> parents = getRecords(record.getParentIds(), 0);

            /* If a record has any *existing* parents it is not indexable */
            if (parents != null && !parents.isEmpty()) {
                record.setIndexable(false);
            }

            /* Assert that the record is set as child on all existing parents */
            for (Record parent : parents) {
                List<String> parentChildren = parent.getChildIds();

                if (parentChildren == null) {
                    parent.setChildIds(Arrays.asList(record.getId()));
                    log.trace ("Creating child list '" + record.getId()
                               + "' on parent " + parent.getId());
                    flushQueue.add (parent);
                } else if (!parentChildren.contains(record.getId())) {
                    parentChildren.add (record.getId());
                    parent.setChildIds(parentChildren);
                    log.trace ("Adding child '" + record.getId()
                               + "' to parent " + parent.getId());
                    flushQueue.add (parent);
                }
            }
        }

        /* Make sure child records are correct */
        if (record.getChildIds() != null) {
            List<Record> children = getRecords(record.getChildIds(), 0);

            /* Assert that the existing child records have this record set
             * as parent and that they are marked not indexable  */
            for (Record child : children) {
                List<String> childParents = child.getParentIds();

                if (childParents == null) {
                    child.setParentIds(Arrays.asList(record.getId()));
                    child.setIndexable(false);
                    log.trace ("Creating parent list '" + record.getId()
                               + " on child " + child.getId());
                    flushQueue.add(child);
                } else if (!childParents.contains(record.getId())) {
                    child.getParentIds().add(record.getId());
                    child.setIndexable(false);
                    log.trace ("Adding parent '" + record.getId()
                               + "' to child " + child.getId());
                    flushQueue.add(child);

                } else {
                    if (child.isIndexable()) {
                        log.debug ("Child '" + child.getId() + "' of '"
                                   + record.getId() + "' was marked as "
                                   + "indexable. Marking as not indexable");
                        child.setIndexable(false);
                    }
                }

            }
        }

        flushAll(flushQueue);

        /* Pseudo-code for new or modified (self = new or modified record):
        if parent exists
          mark self as not indexable
          add self to parent-children
          touch parent upwards (to trigger update)
        foreach child
          mark child as not indexable
          set self as child parent
        foreach child in each record
          if child equals self
            touch record

        Idea: Keep a cache of ghost-childs (child-id's without corresponding
              record) and their Record-id, for faster lookup.

        Pseudo-code for deleted:
          if parent exists
            touch parent upwards
          foreach child
            mark child as indexable
         */
    }

    /**
     * <p>Convenience implementation of {@link WritableStorage#flushAll}
     * simply iterating through the list and calling
     * {@link WritableStorage#flush} on each record.</p>
     *
     * <p>Subclasses of {@code StorageBase} may choose to overwrite this method
     * for optimization purposes.</p>
     *
     * @param records the records to store or update
     * @throws RemoteException on comminication errors
     */
    public void flushAll (List<Record> records) throws RemoteException {
        for (Record rec : records) {
            flush(rec);
        }
    }

    /**
     * Simple implementation of {@link ReadableStorage#getRecords} fetching
     * each record one at a time and collecting them in a list.
     */
    public List<Record> getRecords (List<String> ids, int expansionDepth)
                                                        throws RemoteException {
        ArrayList<Record> result = new ArrayList<Record>(ids.size());
        for (String id : ids) {
            Record r = getRecord(id, expansionDepth);
            if (r != null) {
                result.add(r);
            }
        }

        return result;
    }
    
}


