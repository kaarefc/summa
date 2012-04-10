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
package dk.statsbiblioteket.summa.search.api;

import java.io.Serializable;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.Collection;

import dk.statsbiblioteket.util.qa.QAInfo;
import dk.statsbiblioteket.summa.common.util.ConvenientMap;

/**
 * A request to a SummaSearcher contains arguments to every SearchNode under
 * the Searcher.
 * <p></p>
 * The key-value map of the {@code Request} object is known as the
 * <i>search keys</i>. It is common pratice that searcher implementations
 * supply a {@code SearcherNameKeys} interface defining all the public
 * search keys as {@code static final} strings. See fx.
 * {@link dk.statsbiblioteket.summa.search.api.document.DocumentKeys}
 * </p><p>
 * Note: Underlying empty String-values are equalled to null in getString,
 * getInt, getLong and getBoolean. 
 */
@QAInfo(level = QAInfo.Level.NORMAL,
        state = QAInfo.State.QA_OK,
        author = "te")
public class Request extends ConvenientMap {

}