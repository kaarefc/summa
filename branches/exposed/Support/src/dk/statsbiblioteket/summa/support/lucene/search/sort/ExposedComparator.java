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
package dk.statsbiblioteket.summa.support.lucene.search.sort;

import dk.statsbiblioteket.util.qa.QAInfo;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.logging.Log;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.FieldComparator;
import org.apache.lucene.search.exposed.ExposedFieldComparatorSource;

import java.io.IOException;
import java.util.Locale;

/**
 *
 */
@QAInfo(level = QAInfo.Level.NORMAL,
        state = QAInfo.State.IN_DEVELOPMENT,
        author = "te")
public class ExposedComparator extends ReusableSortComparator {
    private static Log log = LogFactory.getLog(ExposedComparator.class);
    private IndexReader reader = null;
    private Locale locale;
    private ExposedFieldComparatorSource exposedFCS = null;

    public ExposedComparator(String language) {
        super(language);
        locale = language == null || "".equals(language) ? null :
                 new Locale(language);
    }

    @Override
    public FieldComparator newComparator(
        String fieldname, int numHits, int sortPos, boolean reversed)
                                                            throws IOException {
        if (reader == null) {
            throw new IllegalStateException(
                "No reader defined. indexChanged(newreader) must be called at "
                + "least once before requesting a comparator");
        }
        return exposedFCS.newComparator(fieldname, numHits, sortPos, reversed);
    }

    @Override
    public void indexChanged(IndexReader reader) {
        this.reader = reader;
        exposedFCS = new ExposedFieldComparatorSource(reader, locale);
    }
}
