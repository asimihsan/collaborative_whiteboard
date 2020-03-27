package logic;

import com.github.difflib.DiffUtils;
import com.github.difflib.algorithm.DiffException;
import com.github.difflib.algorithm.myers.MyersDiff;
import com.github.difflib.patch.Patch;
import com.github.difflib.patch.PatchFailedException;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import lombok.SneakyThrows;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.w3c.dom.Document;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Given two MxGraph XML documents, one older and one newer, attempt to merge them together.
 *
 * Different clients could be attempting to update the graph so the IDs could collide.
 */

public class MxGraphDocumentMerger {
    private static final Logger log = LogManager.getLogger(MxGraphDocumentMerger.class);

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

    private Set<String> getPatchSourceAndTargetIds(final Patch<NodeWrapper> patch) {
        final Set<String> oldPatchSourceIds = patch
                .getDeltas()
                .stream()
                .map(delta -> delta.getSource().getLines())
                .flatMap(nodeWrappers -> nodeWrappers.stream().map(NodeWrapper::getNodeId))
                .collect(Collectors.toSet());
        final Set<String> oldPatchTargetIds = patch
                .getDeltas()
                .stream()
                .map(delta -> delta.getTarget().getLines())
                .flatMap(nodeWrappers -> nodeWrappers.stream().map(NodeWrapper::getNodeId))
                .collect(Collectors.toSet());
        return Sets.union(oldPatchSourceIds, oldPatchTargetIds);
    }

    @SneakyThrows(PatchFailedException.class)
    private List<NodeWrapper> mergeNodes(final List<NodeWrapper> ancestorNodes,
                                         final List<NodeWrapper> oldNodes,
                                         final List<NodeWrapper> newNodes) {
        log.info("mergeNodes entry");
        final Patch<NodeWrapper> ancestorToOldPatch = diff(ancestorNodes, oldNodes);
        final Patch<NodeWrapper> ancestorToNewPatch = diff(ancestorNodes, newNodes);

        // All mxCell nodes comes with unique IDs. If the old patch IDs do not overlap with the new patch IDs, we
        // can merge them both together. Else we will let the new patch win.
        final Set<String> oldPatchIds = getPatchSourceAndTargetIds(ancestorToOldPatch);
        final Set<String> newPatchIds = getPatchSourceAndTargetIds(ancestorToNewPatch);
        final Set<String> commonIds = Sets.intersection(oldPatchIds, newPatchIds);
        if (commonIds.isEmpty()) {
            log.info("mergeNodes no overlap in IDs, three-way merge proceeding");
            final List<NodeWrapper> newApplied = ancestorToNewPatch.applyTo(ancestorNodes);
            final List<NodeWrapper> oldApplied = ancestorToOldPatch.applyTo(newApplied);
            return ImmutableList.copyOf(oldApplied);
        }

        // TODO we can be more clever here and look for overlapping chunks instead of considering the Patch as a
        // whole. For simplicity for now we skip this opportunity.

        // We can't resolve the conflict, so to allow the whiteboard to make useful progress we allow the new patch to
        // win and clobber the old patch.
        log.info("mergeNodes overlap in IDs, new nodes will clobber old nodes");
        final List<NodeWrapper> newApplied = ancestorToNewPatch.applyTo(ancestorNodes);
        return ImmutableList.copyOf(newApplied);
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
