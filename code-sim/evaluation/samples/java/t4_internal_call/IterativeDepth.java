import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;

public class IterativeDepth {
    public int depth(Node root) {
        if (root == null) {
            return 0;
        }
        Queue<Node> nodes = new ArrayDeque<>();
        nodes.add(root);
        int depth = 0;
        while (!nodes.isEmpty()) {
            int levelSize = nodes.size();
            for (int i = 0; i < levelSize; i++) {
                Node node = nodes.remove();
                nodes.addAll(node.children());
            }
            depth++;
        }
        return depth;
    }

    interface Node {
        List<Node> children();
    }
}
