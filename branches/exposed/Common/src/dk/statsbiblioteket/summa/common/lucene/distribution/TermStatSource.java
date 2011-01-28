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
package dk.statsbiblioteket.summa.common.lucene.distribution;

import dk.statsbiblioteket.summa.common.util.Triple;
import dk.statsbiblioteket.util.Strings;
import dk.statsbiblioteket.util.qa.QAInfo;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.logging.Log;
import org.apache.lucene.index.DocsEnum;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.store.NIOFSDirectory;
import org.apache.lucene.util.BytesRef;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

/**
 * Encapsulates an IndexReader and exposes factory methods for term iterators.
 * </p><p>
 * Iterators returns triples with term, termFrequency and documentFrequency.
 */
@QAInfo(level = QAInfo.Level.NORMAL,
        state = QAInfo.State.IN_DEVELOPMENT,
        author = "te")
public class TermStatSource implements Closeable {
    private static Log log = LogFactory.getLog(TermStatSource.class);

    private final File index;
    private final IndexReader ir;

    public TermStatSource(File index) throws IOException {
        log.debug("Opening index at " + index);
        this.index = index;
        ir = IndexReader.open(new NIOFSDirectory(index));
    }

    @Override
    public void close() throws IOException {
        log.debug("Closing index at " + index);
        ir.close();
    }

    public Iterator<Triple<BytesRef, Long, Long>> getTerms(String field)
                                                            throws IOException {
        if (ir.getSequentialSubReaders() == null) {
            log.debug("getTerms(" + field + ") creating single leaf iterator");
            return new LeafIterator(ir, field);
        }
        IndexReader[] readers = ir.getSequentialSubReaders();
        if (readers.length == 1) {
            log.debug("getTerms(" + field + ") creating single leaf iterator "
                      + "from single sub reader");
            return new LeafIterator(readers[0], field);
        }
        log.debug("getTerms(" + field + ") creating single field iterator "
                  + " with " + readers.length + " readers");
        List<Iterator<Triple<BytesRef, Long, Long>>> providers =
            new ArrayList<Iterator<Triple<BytesRef, Long, Long>>>(
                readers.length);
        for (IndexReader reader: readers) {
            providers.add(new LeafIterator(reader, field));
        }
        return new Merger(providers, field);
    }

    public Iterator<Triple<BytesRef, Long, Long>> getTerms(
        Collection<String> fields) throws IOException {
        String designation = Strings.join(fields, ", ");
        log.debug("Creating multi field iterator for " + designation);
        List<Iterator<Triple<BytesRef, Long, Long>>> sources =
            new ArrayList<Iterator<Triple<BytesRef, Long, Long>>>(
                fields.size());
        for (String field: fields) {
            sources.add(getTerms(field));
        }
        return new TermStatSource.Merger(sources, "multi(" + designation + ")");
    }

    public static class Merger implements
                               Iterator<Triple<BytesRef, Long, Long>> {
        private List<Iterator<Triple<BytesRef, Long, Long>>> providers;
        private List<Triple<BytesRef, Long, Long>> values;
        private String designation;
        private long termCount = 0;

        public Merger(
            List<Iterator<Triple<BytesRef, Long, Long>>> providers,
            String designation) {
            log.debug("Creating Merger(" + designation + ")");
            this.providers = providers;
            this.designation = designation;
            values = new ArrayList<Triple<BytesRef, Long, Long>>(
                providers.size());
            Iterator<Iterator<Triple<BytesRef, Long, Long>>> pi =
                providers.iterator();

            while (pi.hasNext()) {
                Iterator<Triple<BytesRef, Long, Long>> provider = pi.next();
                if (!provider.hasNext()) {
                    log.debug("Merger init: Removed empty provider");
                    pi.remove();
                }
                values.add(provider.next());
            }
        }

        @Override
        public boolean hasNext() {
            return providers.size() > 0;
        }

        @Override
        public Triple<BytesRef, Long, Long> next() {
            if (values.size() != providers.size()) {
                throw new IllegalStateException(
                    "There were " + providers.size() + " and " + values.size() 
                    + " values. The two numbers should be the same. There is "
                    + "an error in the internal bookkeeping");
            }

            int index = -1;
            BytesRef term = null;

            int counter = 0;
            for (Triple<BytesRef, Long, Long> value: values) {
                if (term == null || term.compareTo(value.getValue1()) > 0) {
                    term = value.getValue1();
                    index = counter;
                }
                counter++;
            }

            // Found the term, now sum the stats
            long tf = 0;
            long df = 0;

            if (term == null) {
                throw new IllegalStateException(
                    "Term was null which is illegal at this execution point. "
                    + "There is an error in program logic");
            }

            for (int i = values.size()-1 ; i >= index ; i--) {
                Triple<BytesRef, Long, Long> value = values.get(i);
                if (term.equals(value.getValue1())) {
                    tf += value.getValue2();
                    df += value.getValue3();
                    if (!providers.get(i).hasNext()) {
                        providers.remove(i);
                        values.remove(i);
                    } else {
                        values.set(i, providers.get(i).next());
                    }
                }
            }

            Triple<BytesRef, Long, Long> result =
                new Triple<BytesRef, Long, Long>(term, tf, df);
            termCount++;
            if (termCount == 0) {
                log.debug(
                    "Merger for " + designation + " depleted with " + termCount
                    + " delivered terms");
            }
            if (log.isTraceEnabled()) {
                log.trace("Merging iterator delivered " + result);
            }
            return result;
        }

        @Override
        public void remove() {
            throw new IllegalArgumentException("Not supported");
        }
    }

    private static class LeafIterator implements
                                      Iterator<Triple<BytesRef, Long, Long>> {
        private IndexReader ir;
        private TermsEnum termsEnum;
        private DocsEnum docsEnum = null;
        private long termCount = 0;
        private String field;
        private boolean depleted;

        public LeafIterator(IndexReader ir, String field) throws IOException {
             if (ir.getSequentialSubReaders() != null) {
                 throw new IllegalArgumentException(
                     "Only leaf IndexReaders allowed. Got " + ir);
             }
            this.ir = ir;
            this.field = field;
            Terms terms = ir.fields().terms(field);
            if (terms != null) {
                termsEnum = terms.iterator();
                depleted = termsEnum.next() == null;
            }
        }

        @Override
        public boolean hasNext() {
            return !depleted;
        }

        // TODO: Do we know for sure that the order is Unicode?
        @Override
        public Triple<BytesRef, Long, Long> next() {
            try {
                BytesRef current = termsEnum.term();
                if (current == null) {
                    throw new IllegalStateException(
                        "The next term was null for field '" + field
                        + "', which should never happen");
                }
                // Extracting stats
                long tf = 0;
                long df = 0;
                docsEnum = termsEnum.docs(ir.getDeletedDocs(), docsEnum);
                DocsEnum.BulkReadResult bulk = docsEnum.getBulkResult();
                int read;
                while ((read = docsEnum.read()) != 0) {
                    for (int i = 0 ; i < read ; i++) {
                        tf += bulk.freqs.ints[i];
                        df += bulk.docs.ints[i];
                    }
                }
                final Triple<BytesRef, Long, Long> result =
                    new Triple<BytesRef, Long, Long>(new BytesRef(current), tf, df);
                termCount++;
                depleted = termsEnum.next() == null;
                if (depleted) {
                    if (log.isTraceEnabled()) {
                        log.trace("LeafIterator depleted. Delivered "
                                  + termCount + " term stats");
                    }
                }
                if (log.isTraceEnabled()) {
                   log.trace("LeafIterator delivered " + result);
                }
                return result;
            } catch (IOException e) {
                throw new RuntimeException("Unable to find next term", e);
            }
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException("No removal of terms");
        }
    }
}
