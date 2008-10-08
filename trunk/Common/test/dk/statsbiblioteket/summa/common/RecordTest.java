package dk.statsbiblioteket.summa.common;

import junit.framework.TestCase;

import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;

/**
 * Unit tests for the {@link Record} class
 */
public class RecordTest extends TestCase {

    Record r;
    byte[] emptyContent;

    public void setUp () {
        r = new Record();
        emptyContent = new byte[0];
    }

    public void testSetChildIds () {
        assertNull (r.getChildren());
        assertNull (r.getChildIds());

        r.setChildIds(Arrays.asList("foo", "bar"));

        List<String> childIds = r.getChildIds();
        assertEquals(Arrays.asList("foo", "bar"), childIds);

        // We should not ahve any resolved children
        assertNull (r.getChildren());
    }

    public void testSetParentIds () {
        assertNull (r.getChildren());
        assertNull (r.getChildIds());

        r.setParentIds(Arrays.asList("foo", "bar"));

        List<String> parentIds = r.getParentIds();
        assertEquals(Arrays.asList("foo", "bar"), parentIds);

        // We should not ahve any resolved children
        assertNull (r.getParents());
    }

    public void testSetChildren () {
        assertNull (r.getChildren());
        assertNull (r.getChildIds());

        List<Record> children = new ArrayList<Record>(2);
        children.add(new Record("foo", "base", emptyContent));
        children.add(new Record("bar", "base", emptyContent));

        r.setChildren(children);

        assertEquals(children, r.getChildren());

        // We should updated the child ids accordingly
        assertEquals (Arrays.asList("foo", "bar"), r.getChildIds());
    }

    public void testSetParents () {
        assertNull (r.getParents());
        assertNull (r.getParentIds());

        List<Record> parents = new ArrayList<Record>(2);
        parents.add(new Record("foo", "base", emptyContent));
        parents.add(new Record("bar", "base", emptyContent));

        r.setParents(parents);

        assertEquals(parents, r.getParents());

        // We should updated the child ids accordingly
        assertEquals (Arrays.asList("foo", "bar"), r.getParentIds());
    }
}
