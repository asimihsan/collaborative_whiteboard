package lambda;

import com.google.common.io.Resources;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class EncodingTest {
    private Encoding encoding;

    @BeforeEach
    void setUp() {
        encoding = new Encoding();
    }

    @Test
    public void testEncoder() {
        // === given ===
        final String decoded = loadResourcesFile("encoding01/decoded.txt");
        final String expectedEncoded = loadResourcesFile("encoding01/encoded.txt");

        // === when ===
        final String encoded = encoding.encode(decoded);

        // === then ===
        Assertions.assertEquals(expectedEncoded, encoded);
    }

    @Test
    public void testDecoder() {
        // === given ===
        final String expectedDecoded = loadResourcesFile("encoding01/decoded.txt");
        final String encoded = loadResourcesFile("encoding01/encoded.txt");

        // === when ===
        final String decoded = encoding.decode(encoded);

        // === then ===
        Assertions.assertEquals(expectedDecoded, decoded);
    }

    @SneakyThrows(IOException.class)
    private static String loadResourcesFile(final String filename) {
        return Resources.toString(Resources.getResource(filename), StandardCharsets.UTF_8);
    }
}