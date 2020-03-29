package logic;

import com.google.common.io.Resources;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

class MxGraphExportTest {
    private MxGraphExport mxGraphExport;

    @BeforeEach
    public void setUp() {
        mxGraphExport = new MxGraphExport();
    }

    @Test
    public void test() {
        // === given ===
        final String expectedDoc = loadResourcesFile("example_doc.xml");

        // === when ===
        mxGraphExport.writeImage(expectedDoc);
    }

    @SneakyThrows(IOException.class)
    private static String loadResourcesFile(final String filename) {
        return Resources.toString(Resources.getResource(filename), StandardCharsets.UTF_8);
    }
}