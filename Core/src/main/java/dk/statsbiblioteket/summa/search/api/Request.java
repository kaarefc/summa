/*
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package dk.statsbiblioteket.summa.search.api;

import dk.statsbiblioteket.summa.common.util.ConvenientMap;
import dk.statsbiblioteket.util.qa.QAInfo;

import java.io.Serializable;
import java.util.Map;

/**
 * A request to a SummaSearcher contains arguments to every SearchNode under the Searcher.
 * <p></p>
 * The key-value map of the {@code Request} object is known as the <i>search keys</i>. It is
 * common practice that searcher implementations supply a {@code SearcherNameKeys}
 * interface defining all the public search keys as {@code static final} strings. See fx.
 * {@link dk.statsbiblioteket.summa.search.api.document.DocumentKeys}
 * </p><p>
 * Note: Underlying empty String-values are equalled to null in getString, getInt, getLong and
 * getBoolean. 
 */
@QAInfo(level = QAInfo.Level.NORMAL,
        state = QAInfo.State.QA_OK,
        author = "te")
public class Request extends ConvenientMap {
    private static final long serialVersionUID = 3496587392L;

    public Request(Serializable... args) {
        super(args);
    }

    /**
     * Creates a copy of this Request where all {@code prefixkey-value} pairs with the given prefix are converted
     * to {@code key-value}.
     * @param prefix the prefix to remove where present.
     * @return a new Request where no keys have the given prefix.
     */
    public Request getPrefixAdjustedView(String prefix) {
        Request r = new Request();
        for (Map.Entry<String, Serializable> entry: this.entrySet()) {
            r.put(entry.getKey().startsWith(prefix) ? entry.getKey().substring(prefix.length()) : entry.getKey(),
                  entry.getValue());
        }
        return r;
    }
}




