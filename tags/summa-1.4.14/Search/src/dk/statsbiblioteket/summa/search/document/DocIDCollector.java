/* $Id$
 * $Revision$
 * $Date$
 * $Author$
 *
 * The Summa project.
 * Copyright (C) 2005-2008  The State and University Library
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

import dk.statsbiblioteket.util.qa.QAInfo;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.lucene.search.HitCollector;
import org.apache.lucene.util.OpenBitSet;

import java.io.StringWriter;
import java.util.concurrent.ArrayBlockingQueue;

/**
 * A reusable BitCollector for Lucene searches. After use, {@link #close} /must/
 * be called. This clears the collector and puts it back into the queue of
 * collectors. Failure to call close will result in starvation of collectors.
 * The close is called automatically by SummaSearcherImpl.
 */
@QAInfo(level = QAInfo.Level.NORMAL,
        state = QAInfo.State.IN_DEVELOPMENT,
        author = "te")
public class DocIDCollector extends HitCollector {
    private static Log log = LogFactory.getLog(DocIDCollector.class);

    private ArrayBlockingQueue<DocIDCollector> queue = null;
    /*
     * The Lucene OpenBitSet should be significantly faster than Java's BitSet:
* http://lucene.apache.org/java/2_4_0/api/org/apache/lucene/util/OpenBitSet.html
     */
    private OpenBitSet bits = new OpenBitSet(100000);
    private int docCount = 0;

    /**
     * Constructs a collector and adds it to the given queue.
     * @param queue the queue that this collector is added to.
     */
    public DocIDCollector(ArrayBlockingQueue<DocIDCollector> queue) {
        this.queue = queue;
        while (true) {
            try {
                queue.put(this);
                break;
            } catch (InterruptedException e) {
                log.debug("Interrupted while putting DicIDCollector. Retrying");
            }
        }
    }

    /**
     * Constructs a collector. Normally the other constructor will be used, in
     * order to re-use the collector.
     * @see {@link #DocIDCollector(ArrayBlockingQueue)}.
     */
    public DocIDCollector() {
    }

    @Override
    public void collect(int doc, float score) {
        bits.set(doc);
        docCount++;
    }

    /**
     * Clears the collector and returns it to the queue for reuse.
     * Important: This must be called after use.
     */
    public void close() {
        bits.clear(0, Integer.MAX_VALUE);
        docCount = 0;
        if (queue == null) {
            log.debug("No queue present, so the collector is not put back");
        } else {
            while (true) {
                try {
                    queue.put(this);
                    break;
                } catch (InterruptedException e) {
                    log.debug("Interrupted while putting "
                               + "DocIDCollector. Retrying");
                }
            }
        }
    }

    /**
     * @return the inner BitSet for the collector.
     *         This should only be queried outside of the DicIDCollector-class.
     */
    public OpenBitSet getBits() {
        return bits;
    }

    /**
     * @return the number of collected documents.
     */
    public int getDocCount() {
        return docCount;
    }

    @Override
    public String toString() {
        int MAX_BITS = 10;
        StringWriter sw = new StringWriter(300);
        sw.append("DocIDCollector ").append(Integer.toString(getDocCount()));
        sw.append("(");
        int foundCount = 0;
        int id = 0;
        while ((id = bits.nextSetBit(id))!= -1 && foundCount++ < MAX_BITS) {
            sw.append(Integer.toString(id++)).append(" ");
        }
        sw.append(")");
        return sw.toString();
    }
}


