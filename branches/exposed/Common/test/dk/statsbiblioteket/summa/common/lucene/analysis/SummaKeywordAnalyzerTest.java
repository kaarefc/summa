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
package dk.statsbiblioteket.summa.common.lucene.analysis;

import com.sun.org.apache.xalan.internal.Version;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.core.LowerCaseFilter;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;

import java.io.StringReader;

import junit.framework.TestCase;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;

/**
 * Test cases for {@link SummaKeywordAnalyzer}
 */
public class SummaKeywordAnalyzerTest extends TestCase {

    SummaKeywordAnalyzer a;
    TokenStream t;

    static void assertTokens(TokenStream tokenizer, String... tokens)
                                                               throws Exception{
        CharTermAttribute term = tokenizer.getAttribute(CharTermAttribute.class);
        int count = 0;

        while (tokenizer.incrementToken()) {
            if (count >= tokens.length) {
                fail("Too many tokens from tokenizer, found " + (count+1)
                     + ". Expected " + tokens.length + ".");
            }

            assertEquals("Mismatch in token number " + (count + 1) + ":",
                         tokens[count], term.toString());
            count++;
        }

        assertEquals("To few tokens from tokenizer, found " + count
                     + ". Expected " + tokens.length + ".",
                     tokens.length, count);
    }

    static TokenStream getStream(String text) {
        return new LowerCaseFilter(org.apache.lucene.util.Version.LUCENE_30,
                new WhitespaceTokenizer(
                    org.apache.lucene.util.Version.LUCENE_30,
                    new StringReader(text)));
    }

    public void testFoo() throws Exception {
        a = new SummaKeywordAnalyzer();
        t = a.reusableTokenStream("testField",
                                  new StringReader("foo"));
        assertTokens(t, "foo");

        t = a.tokenStream("testField",
                           new StringReader("foo"));
        assertTokens(t, "foo");
    }

    public void testFooBar() throws Exception {
        a = new SummaKeywordAnalyzer();
        t = a.reusableTokenStream("testField",
                                  new StringReader("foo bar"));
        assertTokens(t, "foo bar");

        t = a.tokenStream("testField",
                          new StringReader("foo bar"));
        assertTokens(t, "foo bar");
    }

    public void testFooBarExtraSpaces() throws Exception {
        a = new SummaKeywordAnalyzer();
        t = a.reusableTokenStream("testField",
                                  new StringReader(" foo  bar   "));
        assertTokens(t, "foo bar");

        t = a.tokenStream("testField",
                           new StringReader(" foo  bar   "));
        assertTokens(t, "foo bar");
    }

    public void testPunctuation() throws Exception {
        a = new SummaKeywordAnalyzer();
        t = a.reusableTokenStream("testField",
                                  new StringReader(".foo  bar."));
        assertTokens(t, "foo bar");

        t = a.tokenStream("testField",
                           new StringReader("foo.bar   "));
        assertTokens(t, "foo bar");
    }

}

