/* $Id: FakeAccess.java,v 1.5 2007/12/04 09:08:19 te Exp $
 * $Revision: 1.5 $
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
/**
 * Created: te 2007-09-04 10:48:55
 * CVS:     $Id: FakeAccess.java,v 1.5 2007/12/04 09:08:19 te Exp $
 */
package dk.statsbiblioteket.summa.storage;

import dk.statsbiblioteket.summa.common.Record;
import dk.statsbiblioteket.summa.storage.StorageBase;
import dk.statsbiblioteket.summa.storage.api.StorageIterator;
import dk.statsbiblioteket.summa.storage.api.QueryOptions;
import dk.statsbiblioteket.util.qa.QAInfo;

import java.rmi.RemoteException;
import java.util.Random;
import java.util.Iterator;
import java.io.IOException;

/**
 * Test helper for RecordIterator.  The pseudo-records contained in this Storage
 * have a sequential integer ID, starting with 0 and going forward. The content
 * of the records are pseudo-randomised.
 * </p><<p>
 * Keys and bases are ignored.
 */
@QAInfo(level = QAInfo.Level.NORMAL,
        state = QAInfo.State.IN_DEVELOPMENT,
        author = "te")
public class FakeAccess extends StorageBase {
    private Random random = new Random();
    private int recordCount;
    private int position = 0; // Where to get the next record

    public FakeAccess(int recordCount) throws IOException {
        this.recordCount = recordCount;
    }

    public long getRecordsFromBase(String base, QueryOptions options)
                                                        throws RemoteException {
        position = 0;
        return 0L;
    }

    public long getRecordsModifiedAfter(long time,
                                        String base, QueryOptions options)
                                                        throws RemoteException {
        position = 0;
        return 0L;
    }

    public long getRecordsFrom(String name, String base, QueryOptions options)
                                                        throws RemoteException {
        if (!recordExists(name)) {
            throw new RemoteException("Record '" + name + "' does not exist");
        }
        int position = Integer.parseInt(name);
        return 0L;
    }

    public Record getRecord(String name, QueryOptions options)
                                                        throws RemoteException {
        try {
            if (recordExists(name)) {
                return produceRecord(name);
            }
            return null;
        } catch (Exception e) {
            throw new RemoteException("Exception getting record '"
                                      + name + "'", e);
        }
    }

    private Record produceRecord(String name) {
        byte[] content = new byte[10];
        random.nextBytes(content);
        return new Record(name, "testbase", content);
    }

    private boolean recordExists(String name) throws RemoteException {
        try {
            int recordID = Integer.parseInt(name);
            return recordID >= 0 && recordID <recordCount;
        } catch (Exception e) {
            return false;
        }
    }

    public void close() throws RemoteException {
        // Does nothing
    }

    public void clearBase(String base) throws RemoteException {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public Record next(long iteratorKey) throws RemoteException {
        if (position >= recordCount) {
            throw new RemoteException("No more records");
        }
        return produceRecord(Integer.toString(position));
    }

    public void flush(Record record) throws RemoteException {
        // Do nothing
    }


}



