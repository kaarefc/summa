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
package dk.statsbiblioteket.summa.common.lucene.distribution;

import dk.statsbiblioteket.util.Strings;
import dk.statsbiblioteket.util.qa.QAInfo;
import dk.statsbiblioteket.util.reader.ReplaceFactory;
import dk.statsbiblioteket.util.reader.ReplaceReader;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Representation of a persistent term with integer-based statistics. Used for
 * representing term frequencies etc.
 */
@QAInfo(level = QAInfo.Level.NORMAL,
        state = QAInfo.State.IN_DEVELOPMENT,
        author = "te")
public class TermEntry implements Comparable<TermEntry> {
    private final String term;
    private final long[] stats;
    private final String[] headings;

    private static final ReplaceReader escaper;
    private static final ReplaceReader unEscaper;
    static {
        Map<String, String> escape = new LinkedHashMap<>(10);
        escape.put("\\", "\\\\");
        escape.put("\n", "\\n");
        escape.put("\t", "\\t");
        escaper = ReplaceFactory.getReplacer(escape);

        Map<String, String> unEscape = new LinkedHashMap<>(10);
        escape.put("\\t", "\\t");
        escape.put("\\n", "\\n");
        escape.put("\\\\", "\\");
        unEscaper = ReplaceFactory.getReplacer(unEscape);

    }


    /**
     * Checks that the amount of stats fits with the provided headings and
     * constructs an entry.
     * @param term     a non-empty String.
     * @param stats    statistics for the String.
     * @param headings Headings for the term and for each stat. The number of
     *                 headings must be equal to the number of stats plus 1.
     *                 Headings are used by {@link #getIndex(String)} and it is
     *                 the responsibility of the caller to ensure that they are
     *                 unique.
     */
    public TermEntry(String term, long[] stats, String[] headings) {
        check(term, stats, headings);
        this.term = term;
        this.stats = stats;
        this.headings = headings;
    }

    /**
     * Constructor that takes the format from {@link #toPersistent()}.
     * Empty Strings or standalone '-' in the stats-section are parsed as 0.
     * @param persistent a textual representation as generated by toPersistent.
     * @param headings   the headings for the TermEntry. The headings must be
     *                   equal to the number of stats plus 1.
     */
    public TermEntry(String persistent, String[] headings) {
        String tokens[] = persistent.split("\t");
        if (tokens.length != headings.length) {
            throw new IllegalArgumentException(String.format(
                "The persistent String '%s' was split in %d tokens. "
                + "Expected %d tokens due to heading '%s'",
                persistent, tokens.length, headings.length,
                Strings.join(headings, ", ")));
        }
        long[] stats = new long[tokens.length-1];
        for (int i = 1 ; i < tokens.length ; i++) {
            stats[i-1] = "".equals(tokens[i]) || "-".equals(tokens[i])
                         ? 0 : Long.parseLong(tokens[i]);
        }
        check(tokens[0], stats, headings);
        this.term = unEscaper.transform(tokens[0]);
        this.stats = stats;
        this.headings = headings;
    }

    private void check(String term, long[] stats, String[] headings) {
        if (term == null || "".equals(term)) {
            throw new IllegalArgumentException(String.format(
                    "Term must be defined, but it was '%s'", term));
        }
        if (stats.length + 1 != headings.length) {
            throw new IllegalArgumentException(
                "The term '" + term + "' has " + stats.length + " stats but "
                + headings.length + ". The number of headings should be one "
                + "more than the number of stats");
        }
    }

    /**
     * Helper method for searching through a collection of persistent entries.
     * Compares the given term to the term encapsulated in persistent.
     * </p><p>
     * The implementation mimics the behaviour of
     * {@link String#compareTo(String)}.
     * @param term       the term to use as source for comparison.
     * @param persistent as generated by {@link #toPersistent}.
     * @return {@code term.compareTo(term_from_persistent)}.
     */
    public static int comparePersistent(String term, String persistent) {
        return term.compareTo(
            unEscaper.transform(persistent.split("\t", 2)[0]));
        // TODO: Switch to optimized version when unit-tests are added
/*        for (int i = 0 ; i < term.length() && i < persistent.length() ; i++) {
            if (term.charAt(i) == persistent.charAt(i)) {
                continue;
            }
            if (persistent.charAt(i) == '\t') {
                
            }
        }*/
    }

    @Override
    public int compareTo(TermEntry o) {
        return term.compareTo(o.getTerm());
    }

    public String getTerm() {
        return term;
    }

    public long[] getStats() {
        return stats;
    }

    public String[] getHeadings() {
        return headings;
    }

    /**
     * Get stat at the given index. Index can be resolved with {@link #getIndex}.
     * @param index the index for the wanted stat.
     * @return the stat at the given index.
     */
    public long getStat(int index) {
        return stats[index];
    }

    /**
     * Summing equivalent to {@link #getStat(int)}.
     * @param indexes the indexes for the stats to sum.
     * @return the sum of the stats for the given indexes.
     */
    public long getSum(int[] indexes) {
        long sum = 0;
        for (int index: indexes) {
            sum += stats[index];
        }
        return sum;
    }

    /**
     * @param heading the wanted heading. If this matches the heading for the
     *                term, -1 is returned.
     * @return the stat-index for the given heading.
     * @throws ArrayIndexOutOfBoundsException if the heading could not be
     *         located.
     */
    public int getIndex(String heading) throws ArrayIndexOutOfBoundsException {
        for (int i = 0 ; i < headings.length ; i++) {
            if (heading.equals(headings[i])) {
                return i-1;
            }
        }
        throw new ArrayIndexOutOfBoundsException(
            "Unable to locate '" + heading + " in "
            + Strings.join(headings, ", "));
    }

    /**
     * The persistence format is the term followed by the stats, divided by tab.
     * Note that headings are not dumped.
     * </p><p>
     * This method temporarily allocates a StringWriter. It is recommended to
     * use {@link #toPersistent(java.io.Writer)} when persisting a non-trivial
     * amount of entries.
     * @return the entry meant for persistence.
     */
    public synchronized String toPersistent() {
        if (stats.length == 0) {
            return term;
        }
        StringWriter sw = new StringWriter(50);
        try {
            toPersistent(sw);
        } catch (IOException e) {
            throw new IllegalStateException(
                "StringWriters should not throw IOExceptions on write, "
                + "but this one did anyway for term '" + term + "'", e);
        }
        return sw.toString();
    }

    /**
     * Writer-oriented version of {@link #toPersistent}. This is the recommended
     * method when persisting a large amount of entries as it does not allocate
     * a new StringWriter.
     * </p><p>
     * Note that no newline o other entry-delimiter is written. It is the
     * responsibility of the caller to ensure proper entry-delimiters.
     * @param writer the destination for the persistent format.
     * @throws IOException if the Writer could not write.
     */
    public synchronized void toPersistent(Writer writer) throws IOException {
        writer.append(escaper.transform(term));
        for (long stat: stats) {
            writer.append("\t").append(Long.toString(stat));
        }
    }

    /**
     * Absorb the other entry into this by adding the stats. For efficiency it
     * is assumed that the headings match.
     * @param other the term entry to absorb into this.
     */
    public void absorb(TermEntry other) {
        if (!term.equals(other.getTerm())) {
            throw new IllegalArgumentException(String.format(
                    "The term must match. This term was '%s', other "
                    + "term was '%s'", term, other.getTerm()));
        }
        if (stats.length != other.getStats().length) {
            throw new IllegalArgumentException(
                    "The number of stats must match. This term had "
                    + stats.length + " while the other had "
                    + other.getStats().length);
        }
        for (int i = 0 ; i < stats.length ; i++) {
            stats[i] += other.getStats()[i];
        }
    }

    /**
     * Constructs a new entry by appending the stats from the other entry to the
     * stats from this entry. The combined headings are assigned to the new
     * entry.
     * @param other            another entry with the same term.
     * @param combinedHeadings the headings for the new entry.
     * @return a new entry based on this and other entry's stats.
     */
    public TermEntry add(TermEntry other, String[] combinedHeadings) {
        if (!term.equals(other.getTerm())) {
            throw new IllegalArgumentException(String.format(
                    "The term must match. This term was '%s', other "
                    + "term was '%s'", term, other.getTerm()));
        }
        long[] newStats = new long[stats.length + other.getStats().length];
        System.arraycopy(stats, 0, newStats, 0, stats.length);
        System.arraycopy(other.getStats(), 0, newStats, stats.length,
                         other.getStats().length);
        return new TermEntry(term, newStats, combinedHeadings);
    }

    @Override
    public String toString() {
        StringWriter sw = new StringWriter();
        sw.append("TermEntry('").append(term).append("', [");
        for (int i = 0 ; i < stats.length ; i++) {
            if (i > 0) {
                sw.append(", ");
            }
            sw.append(Long.toString(stats[i]));
        }
        sw.append("])");
        return sw.toString();
    }
}
