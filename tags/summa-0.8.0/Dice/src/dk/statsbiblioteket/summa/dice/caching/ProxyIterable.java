/* $Id: ProxyIterable.java,v 1.2 2007/10/04 13:28:17 te Exp $
 * $Revision: 1.2 $
 * $Date: 2007/10/04 13:28:17 $
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
package dk.statsbiblioteket.summa.dice.caching;

import java.util.Iterator;

import dk.statsbiblioteket.util.qa.QAInfo;

/**
 * Wrap an {@link Iterator} in a {@link Iterable}.
 */
@QAInfo(level = QAInfo.Level.NORMAL,
        state = QAInfo.State.IN_DEVELOPMENT,
        author = "mke",
        comment = "Needs javadocs")
public class ProxyIterable implements Iterable {

    Iterator backend;

    public ProxyIterable (Iterator iter) {
        backend = iter;
    }

    public Iterator iterator () {
        return backend;
    }
}