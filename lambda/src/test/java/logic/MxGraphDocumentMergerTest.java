package logic;

import com.google.common.io.Resources;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

class MxGraphDocumentMergerTest {
    private MxGraphDocumentMerger merger;

    @BeforeEach
    public void setUp() {
        final XmlUtils xmlUtils = new XmlUtils();
        merger = new MxGraphDocumentMerger(xmlUtils);
    }

    /**
     * There's a merge needed, but both the old and new documents add one new node on top of the common ancestor.
     * If we did a plain two-way merge we'd lose one or the other new nodes. With a three-way merge we will always
     * keep both new nodes, regardless of what clever algorithm we use.
     *
     * e.g. imagine the ancestor is "foo", old is "foo\nbar", and new is "foo\nbaz". Our desired result is
     * "foo\nbar\nbaz", but the order of bar and baz is arbitrary (this test assumes we prefer the old ordering).
     * Arbitrary ordering with mxGraph isn't so bad because this "just" affects the z-order.
     *
     * Note that this is a conflict; Git would ask a human to resolve this. Here we favor the old doc, but really
     * don't care because this is just a z-order difference.
     */
    @Test
    public void testMergeWithTwoNewNodes() {
        // === given ===
        final String ancestorDoc = loadResourcesFile("add_two/doc003_ancestor.xml");
        final String oldDoc = loadResourcesFile("add_two/doc003_old.xml");
        final String newDoc = loadResourcesFile("add_two/doc003_new.xml");
        final String expectedDoc = loadResourcesFile("add_two/doc003_expected.xml");

        // === when ===
        final String merged = merger.merge(ancestorDoc, oldDoc, newDoc);

        // === then ===
        System.out.println(merged);
        Assertions.assertEquals(expectedDoc, merged);
    }

    /**
     * In mxGraph the order of nodes under root indicates z-order. Our diff algorithm should not duplicate elements
     * if just the z-order changes.
     */
//    @Test
//    public void testZOrderChange() {
//        // === given ===
//        final String oldFile = loadResourcesFile("zorder/doc001_old.xml");
//        final String newFile = loadResourcesFile("zorder/doc001_new.xml");
//        final String expectedFile = loadResourcesFile("zorder/doc001_expected.xml");
//
//        // === when ===
//        final String merged = merger.merge(oldFile, newFile);
//
//        // === then ===
//        Assertions.assertEquals(expectedFile, merged);
//    }

    /**
     * If the user deletes a node, then check the merged document reflects the node is deleted.
     */
//    @Test
//    public void testDeleteOneNode() {
//        // === given ===
//        final String oldFile = loadResourcesFile("delete_one/doc002_old.xml");
//        final String newFile = loadResourcesFile("delete_one/doc002_new.xml");
//        final String expectedFile = loadResourcesFile("delete_one/doc002_expected.xml");
//
//        // === when ===
//        final String merged = merger.merge(oldFile, newFile);
//
//        // === then ===
//        System.out.println(merged);
//        Assertions.assertEquals(expectedFile, merged);
//    }

    @SneakyThrows(IOException.class)
    private static String loadResourcesFile(final String filename) {
        return Resources.toString(Resources.getResource(filename), StandardCharsets.UTF_8);
    }
}