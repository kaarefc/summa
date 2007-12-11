/* $Id: Status.java,v 1.6 2007/10/11 12:56:25 te Exp $
 * $Revision: 1.6 $
 * $Date: 2007/10/11 12:56:25 $
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
package dk.statsbiblioteket.summa.score.api;

import dk.statsbiblioteket.util.qa.QAInfo;

import java.io.Serializable;

/**
 * Generic object to represent the status of a Score service or client.
 * @see Service
 * @see dk.statsbiblioteket.summa.score.client.Client
 */
@QAInfo(level = QAInfo.Level.NORMAL,
        state = QAInfo.State.IN_DEVELOPMENT,
        author = "mke")
public class Status implements Serializable {
    public static enum CODE {
        /**
         * This object has been constructed and is ready for start.
         */
        constructed,
        /**
         * The object is initialising its state.
         */
        startingUp,
        /**
         * The object is ready for action.
         */
        idle,
        /**
         * The object state is running - further requests to the object might
         * be delayed.
         */
        running,
        /**
         * The object has experienced a failure and is recovering.
         */
        recovering,
        /**
         * The object has been intentionally terminated.
         */
        stopping,
        /**
         * The object has been intentionally terminated.
         */
        stopped,
        /**
         * The structure of the object has been compromised, so that it
         * can no longer function. Termination is required.
         */
        crashed
    }

    private CODE code;
    private String message;

    /**
     * Create a new Status object.
     * @param code Status code for status object
     * @param message Message with explanatory text
     */
    public Status(CODE code, String message) {
        this.code = code;
        this.message = message;
    }

    public String toString() {
        return "<"+code+":"+message+">";
    }

    public CODE getCode () {
        return code;
    }
}
