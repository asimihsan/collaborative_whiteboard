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
        merger = new MxGraphDocumentMerger();
    }

    /**
     * In mxGraph the order of nodes under root indicates z-order. Our diff algorithm should not duplicate elements
     * if just the z-order changes.
     */
    @Test
    public void testZOrderChange() {
        // === given ===
        final String oldFile = loadResourcesFile("zorder/doc001_old.xml");
        final String newFile = loadResourcesFile("zorder/doc001_new.xml");
        final String expectedFile = loadResourcesFile("zorder/doc001_expected.xml");

        // === when ===
        final String merged = merger.merge(oldFile, newFile);

        // === then ===
        Assertions.assertEquals(expectedFile, merged);
    }

    /**
     * If the user deletes a node, then check the merged document reflects the node is deleted.
     */
    @Test
    public void testDeleteOneNode() {
        // === given ===
        final String oldFile = loadResourcesFile("delete_one/doc002_old.xml");
        final String newFile = loadResourcesFile("delete_one/doc002_new.xml");
        final String expectedFile = loadResourcesFile("delete_one/doc002_expected.xml");

        // === when ===
        final String merged = merger.merge(oldFile, newFile);

        // === then ===
        System.out.println(merged);
        Assertions.assertEquals(expectedFile, merged);
    }

    @SneakyThrows(IOException.class)
    private static String loadResourcesFile(final String filename) {
        return Resources.toString(Resources.getResource(filename), StandardCharsets.UTF_8);
    }
}