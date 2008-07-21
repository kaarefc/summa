/* $Id: DiskStringPool.java,v 1.3 2007/10/04 13:28:21 te Exp $
 * $Revision: 1.3 $
 * $Date: 2007/10/04 13:28:21 $
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
/*
 * The State and University Library of Denmark
 * CVS:  $Id: DiskStringPool.java,v 1.3 2007/10/04 13:28:21 te Exp $
 */
package dk.statsbiblioteket.summa.facetbrowser.util.pool;

import java.io.IOException;
import java.io.File;
import java.text.Collator;

import dk.statsbiblioteket.util.qa.QAInfo;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Simple implementation of Strings with DiskPool.
 * The persistent files used by this implementation are compatible with those
 * from {@link MemoryStringPool}.
 */
@QAInfo(level = QAInfo.Level.NORMAL,
        state = QAInfo.State.QA_NEEDED,
        author = "te")
public class DiskStringPool extends DiskPool<String> implements
                                                     CollatorSortedPool {
    private Log log = LogFactory.getLog(DiskStringPool.class);

    private Collator collator = null;

    public DiskStringPool() throws IOException {
        super();
    }

    public DiskStringPool(File location, String poolName,
                          boolean newPool) throws IOException {
        super(location, poolName, newPool);
        log.debug(String.format(
                "Constructed pool '%s' for location '%s'", poolName, location));
    }

    protected byte[] valueToBytes(String value) {
        return StringConverter.valueToBytes(value);
    }

    protected String bytesToValue(byte[] buffer, int length) {
        return StringConverter.bytesToValue(buffer, length);
    }

    public int compare(String o1, String o2) {
        return collator == null? o1.compareTo(o2) : collator.compare(o1, o2);
    }

    /* Mutators */

    public Collator getCollator() {
        return collator;
    }
    public void setCollator(Collator collator) {
        this.collator = collator;
    }


}
