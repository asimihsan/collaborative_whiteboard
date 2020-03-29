package logic;

import com.mxgraph.io.mxCodec;
import com.mxgraph.util.mxCellRenderer;
import com.mxgraph.view.mxGraph;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import org.w3c.dom.Document;

import javax.imageio.ImageIO;
import javax.xml.parsers.SAXParserFactory;
import java.awt.image.BufferedImage;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Export mxGraph documents to images or PDFs.
 */
public class MxGraphExport {
    private static final SAXParserFactory parserFactory = SAXParserFactory.newInstance();

    private final XmlUtils xmlUtils;

    public MxGraphExport() {
        this(new XmlUtils());
    }

    public MxGraphExport(final XmlUtils xmlUtils) {
        this.xmlUtils = checkNotNull(xmlUtils);
    }

    /**
     * TODO document and refactor, just playing around right now
     * @param xml
     */
    @SneakyThrows
    public void writeImage(final String xml) {

        final Document document = xmlUtils.loadXml(xml);
        final mxGraph graph = new mxGraph();
        final mxCodec codec = new mxCodec(document);
        codec.decode(document.getDocumentElement(), graph.getModel());

        final BufferedImage image = mxCellRenderer.createBufferedImage(
                graph,
                null /*cells*/,
                1 /*scale*/,
                null /*background*/,
                true /*antiAlias*/,
                null /*clip*/);
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (final BufferedOutputStream bos = new BufferedOutputStream(baos)) {
            ImageIO.write(image, "png", bos);
        }
        FileUtils.writeByteArrayToFile(new File("/tmp/foo.png"), baos.toByteArray());
    }
}
