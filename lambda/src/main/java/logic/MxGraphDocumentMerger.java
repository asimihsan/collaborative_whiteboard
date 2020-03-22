package logic;

import lombok.SneakyThrows;
import org.atteo.xmlcombiner.XmlCombiner;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Given two MxGraph XML documents, one older and one newer, attempt to merge them together.
 *
 * Different clients could be attempting to update the graph so the IDs could collide.
 */

public class MxGraphDocumentMerger {

    @SneakyThrows({ParserConfigurationException.class, IOException.class, SAXException.class, TransformerException.class})
    public String merge(final String olderDocument, final String newerDocument) {
        final XmlCombiner combiner = new XmlCombiner();

        final InputStream olderDocumentIs = new ByteArrayInputStream(olderDocument.getBytes(StandardCharsets.UTF_8));
        final InputStream newerDocumentIs = new ByteArrayInputStream(newerDocument.getBytes(StandardCharsets.UTF_8));

        combiner.combine(olderDocumentIs);
        combiner.combine(newerDocumentIs);

        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        combiner.buildDocument(baos);

        return baos.toString(StandardCharsets.UTF_8);
    }
}
