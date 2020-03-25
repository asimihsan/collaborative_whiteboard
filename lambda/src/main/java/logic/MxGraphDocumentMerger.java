package logic;

import com.github.difflib.DiffUtils;
import com.github.difflib.algorithm.myers.MyersDiff;
import com.github.difflib.algorithm.DiffException;
import com.github.difflib.patch.Patch;
import com.google.common.collect.ImmutableList;
import lombok.SneakyThrows;
import org.w3c.dom.Document;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiPredicate;

/**
 * Given two MxGraph XML documents, one older and one newer, attempt to merge them together.
 *
 * Different clients could be attempting to update the graph so the IDs could collide.
 */

public class MxGraphDocumentMerger {
    private final XmlUtils xmlUtils;

    public MxGraphDocumentMerger() {
        this(new XmlUtils());
    }

    public MxGraphDocumentMerger(final XmlUtils xmlUtils) {
        this.xmlUtils = xmlUtils;
    }

    @SneakyThrows(DiffException.class)
    private <T> Patch<T> diff(final List<T> original, final List<T> revised) {
        return DiffUtils.diff(original, revised, new MyersDiff<>());
    }

    private List<NodeWrapper> mergeNodes(final List<NodeWrapper> commonAncestorNodes,
                                         final List<NodeWrapper> oldNodes,
                                         final List<NodeWrapper> newNodes) {
        final Patch<NodeWrapper> ancestorToOldPatch = diff(commonAncestorNodes, oldNodes);
        final Patch<NodeWrapper> ancestorToNewPatch = diff(commonAncestorNodes, newNodes);

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

    public String merge(final String commonAncestorDocumentString,
                        final String oldDocumentString,
                        final String newDocumentString) {
        final List<NodeWrapper> commonAncestorNodes = xmlUtils.getMxCellNodes(commonAncestorDocumentString);
        final List<NodeWrapper> oldNodes = xmlUtils.getMxCellNodes(oldDocumentString);
        final List<NodeWrapper> newNodes = xmlUtils.getMxCellNodes(newDocumentString);
        final List<NodeWrapper> mergedNodes = mergeNodes(commonAncestorNodes, oldNodes, newNodes);

        final Document outputDocument = xmlUtils.createMxGraphModelDocument(mergedNodes);
        return xmlUtils.documentToString(outputDocument);
    }
}
