package lambda;

import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.DeflaterInputStream;
import java.util.zip.InflaterInputStream;

public class Encoding {

    @SneakyThrows(IOException.class)
    public String decode(final String encoded) {
        final Base64.Decoder base64Decoder = Base64.getDecoder();
        final byte[] compressed = base64Decoder.decode(encoded);

        final StringWriter writer = new StringWriter();
        try (final ByteArrayInputStream bais = new ByteArrayInputStream(compressed);
             final InflaterInputStream iis = new InflaterInputStream(bais)) {
            IOUtils.copy(iis, writer, StandardCharsets.UTF_8);
        }
        return writer.toString();
    }

    @SneakyThrows(IOException.class)
    public String encode(final String unencoded) {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final InputStream is = IOUtils.toInputStream(unencoded, StandardCharsets.UTF_8);
        try (final DeflaterInputStream dis = new DeflaterInputStream(is)) {
            IOUtils.copy(dis, baos);
        }

        final Base64.Encoder base64Encoder = Base64.getEncoder();
        return base64Encoder.encodeToString(baos.toByteArray());
    }
}
