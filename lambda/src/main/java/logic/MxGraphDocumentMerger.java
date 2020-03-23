package logic;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import lombok.AllArgsConstructor;
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
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Given two MxGraph XML documents, one older and one newer, attempt to merge them together.
 *
 * Different clients could be attempting to update the graph so the IDs could collide.
 */

public class MxGraphDocumentMerger {
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

    @AllArgsConstructor
    private static class NodeWrapper {
        private final Node node;

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof NodeWrapper)) {
                return false;
            }
            final NodeWrapper that = (NodeWrapper) o;
            return node.isEqualNode(that.node);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(node);
        }

        public Node getNode() {
            return node;
        }

        public String getNodeId() {
            return node.getAttributes().getNamedItem("id").getNodeValue();
        }
    }

    @SneakyThrows({SAXException.class, IOException.class, ParserConfigurationException.class})
    private Document loadXml(final String text) {
        final DocumentBuilder builder = documentBuilderFactory.newDocumentBuilder();
        final InputStream is = new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
        final Document document = builder.parse(is);
        document.getDocumentElement().normalize();
        return document;
    }

    private List<NodeWrapper> getMxCellNodes(final String documentString) {
        final Document document = loadXml(documentString);
        final NodeList cells = document.getElementsByTagName("mxCell");
        final ImmutableList.Builder<NodeWrapper> listBuilder = ImmutableList.builderWithExpectedSize(cells.getLength());
        for (int i = 0; i < cells.getLength(); i++) {
            listBuilder.add(new NodeWrapper(cells.item(i)));
        }
        return listBuilder.build();
    }

    @SneakyThrows(ParserConfigurationException.class)
    private Document createMxGraphModelDocument(final List<NodeWrapper> nodes) {
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

    @SneakyThrows({TransformerConfigurationException.class, TransformerException.class})
    private String documentToString(final Document document) {
        final Transformer transformer = transformerFactory.newTransformer();
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        final DOMSource domSource = new DOMSource(document);
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final StreamResult streamResult = new StreamResult(baos);
        transformer.transform(domSource, streamResult);
        return baos.toString(StandardCharsets.UTF_8);
    }

    private List<NodeWrapper> mergeNodes(final List<NodeWrapper> oldNodes, final List<NodeWrapper> newNodes) {
        final Set<String> seenIds = new HashSet<>();
        final List<NodeWrapper> mergedNodes = new ArrayList<>();
        for (int oldIndex = 0, newIndex = 0;
             !(oldIndex >= oldNodes.size() && newIndex >= newNodes.size());
        ) {
            if (oldIndex >= oldNodes.size()) {
                final NodeWrapper newNode = newNodes.get(newIndex);
                if (!seenIds.contains(newNode.getNodeId())) {
                    mergedNodes.add(newNode);
                    seenIds.add(newNode.getNodeId());
                }
                newIndex++;
                continue;
            }
            if (newIndex >= newNodes.size()) {
                oldIndex++;
                continue;
            }
            final NodeWrapper oldNode = oldNodes.get(oldIndex);
            final NodeWrapper newNode = newNodes.get(newIndex);
            if (oldNode.equals(newNode)) {
                if (!seenIds.contains(oldNode.getNodeId())) {
                    mergedNodes.add(oldNode);
                    seenIds.add(oldNode.getNodeId());
                }
                oldIndex++;
                newIndex++;
                continue;
            }
            if (!seenIds.contains(newNode.getNodeId())) {
                mergedNodes.add(newNode);
                seenIds.add(newNode.getNodeId());
            }
            newIndex++;
        }
        return ImmutableList.copyOf(mergedNodes);
    }

    public String merge(final String olderDocumentString, final String newerDocumentString) {
        final List<NodeWrapper> oldNodes = getMxCellNodes(olderDocumentString);
        final List<NodeWrapper> newNodes = getMxCellNodes(newerDocumentString);
        final List<NodeWrapper> mergedNodes = mergeNodes(oldNodes, newNodes);

        final Document outputDocument = createMxGraphModelDocument(mergedNodes);
        return documentToString(outputDocument);
    }
}
