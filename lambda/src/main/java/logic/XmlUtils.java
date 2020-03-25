package logic;

import com.google.common.collect.ImmutableList;
import lombok.SneakyThrows;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Scanner;

public class XmlUtils {
    private static final DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newDefaultInstance();
    static {
        documentBuilderFactory.setValidating(false);
        documentBuilderFactory.setIgnoringElementContentWhitespace(true);
        documentBuilderFactory.setIgnoringComments(true);
    }
    private static final TransformerFactory transformerFactory = TransformerFactory.newDefaultInstance();
    static {
        transformerFactory.setAttribute("indent-number", 4);
    }

    @SneakyThrows({SAXException.class, IOException.class, ParserConfigurationException.class})
    private Document loadXml(final String text) {
        final DocumentBuilder builder = documentBuilderFactory.newDocumentBuilder();
        final InputStream is = new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
        final Document document = builder.parse(is);
        document.getDocumentElement().normalize();
        return document;
    }

    /**
     * Given raw XML return a list of NodeWrapper instances, which wrap underlying XML nodes. We need to wrap them
     * because they don't support equals() and hashCode(), which we need for diff'ing.
     *
     * @param documentString Raw String of XML.
     * @return A list of NodeWrapper instances, for just the mxCell elements.
     */
    public List<NodeWrapper> getMxCellNodes(final String documentString) {
        final Document document = loadXml(documentString);
        final NodeList cells = document.getElementsByTagName("mxCell");
        final ImmutableList.Builder<NodeWrapper> listBuilder = ImmutableList.builderWithExpectedSize(cells.getLength());
        for (int i = 0; i < cells.getLength(); i++) {
            listBuilder.add(new NodeWrapper(cells.item(i)));
        }
        return listBuilder.build();
    }

    /**
     * Note that an mxGraph document is one mxGraphModel node, with a single root child, and that root child
     * contains a flat list of mxCell items. The mxCell items seem to either be flat with just attributes or
     * sometimes contains an mxGeometry child.
     *
     * That is why we can confidently consider an empty drawing to be a mxGraphModel, with a single root child. Then
     * we will in "nodes".
     *
     * @param nodes {@link NodeWrapper} instances that wrap underlying XML nodes. We need to wrap them because they do
     *              not natively support equals or hashCode, yet we need those methods for diff'ing.
     *
     * @return An XML document that can be "transformed" (rendered) to text.
     */
    @SneakyThrows(ParserConfigurationException.class)
    public Document createMxGraphModelDocument(final List<NodeWrapper> nodes) {
        final DocumentBuilder builder = documentBuilderFactory.newDocumentBuilder();
        final Document document = builder.newDocument();
        final Element mxGraphModel = document.createElement("mxGraphModel");
        document.appendChild(mxGraphModel);
        final Element root = document.createElement("root");
        mxGraphModel.appendChild(root);
        for (final NodeWrapper nodeWrapper : nodes) {
            final Node node = nodeWrapper.getNode();
            final Node importedNode = document.importNode(node, true /*deep*/);
            root.appendChild(importedNode);
        }
        return document;
    }

    /**
     * Render an XML document DOM object to a UTF-8 encoded String.
     *
     * @param document XML document DOM root.
     * @return A rendered String.
     */
    @SneakyThrows({TransformerConfigurationException.class, TransformerException.class})
    public String documentToString(final Document document) {
        final Transformer transformer = transformerFactory.newTransformer();
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        final DOMSource domSource = new DOMSource(document);
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final StreamResult streamResult = new StreamResult(baos);
        transformer.transform(domSource, streamResult);
        final String result = baos.toString(StandardCharsets.UTF_8);
        return removeBlankLines(result);
    }

    private String removeBlankLines(final String input) {
        final ByteArrayInputStream bais = new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8));
        final ByteArrayOutputStream baos = new ByteArrayOutputStream(input.length());
        try (final Scanner scanner = new Scanner(bais)) {
            while (scanner.hasNextLine()) {
                final String line = scanner.nextLine();
                final String lineTrimmed = line.trim();
                if (lineTrimmed.length() > 0) {
                    baos.writeBytes(line.getBytes());
                    baos.write('\n');
                }
            }
        }
        return baos.toString(StandardCharsets.UTF_8);
    }
}
