import java.util.List;

public class RecursiveDepth {
    public int depth(Node node) {
        if (node == null) {
            return 0;
        }
        int best = 0;
        for (Node child : node.children()) {
            best = Math.max(best, depth(child));
        }
        return best + 1;
    }

    interface Node {
        List<Node> children();
    }
}
