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
package dk.statsbiblioteket.summa.support.alto.as2;

import dk.statsbiblioteket.summa.common.configuration.Configuration;
import dk.statsbiblioteket.summa.support.alto.AltoAnalyzerBase;
import dk.statsbiblioteket.summa.support.alto.AltoParser;
import dk.statsbiblioteket.util.qa.QAInfo;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

@QAInfo(level = QAInfo.Level.NOT_NEEDED,
        state = QAInfo.State.QA_OK,
        author = "te")
public class AS2AltoParser extends AltoParser {
    private static Log log = LogFactory.getLog(AS2AltoParser.class);

    public AS2AltoParser(Configuration conf) {
        super(conf);
    }

    @Override
    protected AltoAnalyzerBase createAnalyzer(Configuration conf) {
        log.trace("Creating analyzer");
        return new AS2AltoAnalyzer(conf);
    }

    public String toString() {
        return "AS2AltoParser()";
    }
}
