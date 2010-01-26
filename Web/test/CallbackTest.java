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

import dk.statsbiblioteket.util.qa.QAInfo;
import dk.statsbiblioteket.util.xml.DOM;
import dk.statsbiblioteket.util.xml.XSLT;
import dk.statsbiblioteket.summa.common.configuration.Resolver;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.logging.Log;
import org.w3c.dom.Document;
import junit.framework.TestCase;

import java.util.Properties;
import java.io.File;

/**
 *
 */
@QAInfo(level = QAInfo.Level.NORMAL,
        state = QAInfo.State.IN_DEVELOPMENT,
        author = "te")
public class CallbackTest extends TestCase {
    private static Log log = LogFactory.getLog(CallbackTest.class);

    private static final String XSLTLocationString =
            "data/transformCallback/getLikes.xsl";
    private static final String XMLLocationString =
            "data/transformCallback/callback_input.xml";


    @SuppressWarnings({"deprecation"})
    public void testXMLOperations() throws Exception {


        Properties prop = new Properties();

        prop.put("bundle_global", "globals");
        prop.put("bundle_availability", "availability");
        prop.put("locale", "da");


        System.out.println(XSLT.transform(
                Resolver.getURL(XSLTLocationString),
                Resolver.getUTF8Content(XMLLocationString),
                prop, true));
    }


}

