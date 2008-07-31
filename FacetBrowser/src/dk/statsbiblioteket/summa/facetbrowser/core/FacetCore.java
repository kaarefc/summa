/* $Id: FacetCore.java,v 1.5 2007/10/05 10:20:22 te Exp $
 * $Revision: 1.5 $
 * $Date: 2007/10/05 10:20:22 $
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
package dk.statsbiblioteket.summa.facetbrowser.core;

import dk.statsbiblioteket.util.qa.QAInfo;

import java.io.IOException;
import java.io.File;

/**
 * The core is responsible for loading existing Facet-structures and for
 * handling Facet setup in the form of configuration.
 */
@QAInfo(level = QAInfo.Level.NORMAL,
        state = QAInfo.State.IN_DEVELOPMENT,
        author = "te")
public interface FacetCore {

    /**
     * Open a Facet structure at the given location. If there is no existing
     * structure, .
     * @param directory the location of the data.
     * @throws IOException if the data could not be read from the file system.
     */
    public void open(File directory) throws IOException;

    /**
     * Checks to see if the internal facet representation is synchronized to
     * the Lucene index and the configuration. The check is not guaranteed to
     * deliver the correct result, only a strong indication.
     * @return true if the internal representation is in sync with the index.
     */
    public boolean isSynchronized();
}
