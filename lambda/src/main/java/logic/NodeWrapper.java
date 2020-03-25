package logic;

import com.google.common.hash.Hashing;
import lombok.AllArgsConstructor;
import org.w3c.dom.Node;

import java.nio.charset.StandardCharsets;

@AllArgsConstructor
public class NodeWrapper {
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

    /**
     * Tries to be a good hash function by hashing the node's ID. Note that part of the mxGraph contract
     * seems to be that all the nodes have a unique ID.
     *
     * @return hash code
     */
    @Override
    public int hashCode() {
        return Hashing.goodFastHash(32)
                .newHasher()
                .putString(getNodeId(), StandardCharsets.UTF_8)
                .hash()
                .asInt();
    }

    public Node getNode() {
        return node;
    }

    public String getNodeId() {
        return node.getAttributes().getNamedItem("id").getNodeValue();
    }
}
