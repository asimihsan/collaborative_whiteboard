package logic;

import com.google.common.io.Resources;
import lombok.SneakyThrows;
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

    @Test
    public void simpleTest() {
        // === given ===
        final String oldFile = loadResourcesFile("doc001_old.xml");
        final String newFile = loadResourcesFile("doc001_new.xml");

        // === when ===
        final String merged = merger.merge(oldFile, newFile);

        // === then ===
        System.out.println(merged);
    }

    @SneakyThrows(IOException.class)
    private static String loadResourcesFile(final String filename) {
        return Resources.toString(Resources.getResource(filename), StandardCharsets.UTF_8);
    }
}