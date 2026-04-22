// Ziqi Liu Meng Project-Based Software Engineering
// Tree Edit Distance (TED) via the Zhang-Shasha algorithm.
//
// Reference: Zhang & Shasha (1989), "Simple fast algorithms for the editing
// distance between trees and related problems", SIAM J. Comput. 18(6).
//
// The result is identical to APTED (Pawlik & Augsten, 2015) for ordered trees;
// Zhang-Shasha is simpler to implement from scratch and runs in O(n²·m²) time,
// which is well within budget for method-level ASTs (typically < 100 nodes).
//
// Cost model (all costs = 1):
//   delete  – remove a node, its children become children of its parent
//   insert  – symmetric of delete
//   rename  – change the label of a node (cost 0 when labels are equal)
package com.ziqi.codesim.ast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TreeEditDistance {

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Compute the minimum-cost tree edit distance between two labeled ordered trees.
     *
     * @return non-negative integer edit distance
     */
    public static int compute(TedNode t1, TedNode t2) {
        // Flatten both trees into post-order arrays
        List<String>  labels1 = new ArrayList<>();
        List<Integer> lmdList1 = new ArrayList<>();
        postOrder(t1, labels1, lmdList1);

        List<String>  labels2 = new ArrayList<>();
        List<Integer> lmdList2 = new ArrayList<>();
        postOrder(t2, labels2, lmdList2);

        int n1 = labels1.size();
        int n2 = labels2.size();

        // Edge cases: empty trees
        if (n1 == 0 && n2 == 0) return 0;
        if (n1 == 0) return n2;
        if (n2 == 0) return n1;

        int[] lm1 = lmdList1.stream().mapToInt(x -> x).toArray();
        int[] lm2 = lmdList2.stream().mapToInt(x -> x).toArray();

        int[] kr1 = keyroots(lm1, n1);
        int[] kr2 = keyroots(lm2, n2);

        // td[i][j] = TED between subtree rooted at node i (T1) and node j (T2)
        // Filled incrementally as keyroot pairs are processed.
        int[][] td = new int[n1][n2];

        for (int i1 : kr1) {
            for (int i2 : kr2) {
                int l1 = lm1[i1]; // leftmost leaf of i1's subtree
                int l2 = lm2[i2];

                // fd[a][b] = forest distance between
                //   the forest of nodes [l1 .. l1+a-1] in T1 and
                //   the forest of nodes [l2 .. l2+b-1] in T2.
                // Indices: a = i − l1 + 1,  b = j − l2 + 1.
                int rows = i1 - l1 + 2;
                int cols = i2 - l2 + 2;
                int[][] fd = new int[rows][cols];

                // Base cases: deleting / inserting every node in one forest
                for (int a = 1; a < rows; a++) fd[a][0] = fd[a - 1][0] + 1;
                for (int b = 1; b < cols; b++) fd[0][b] = fd[0][b - 1] + 1;

                for (int i = l1; i <= i1; i++) {
                    int a = i - l1 + 1;
                    for (int j = l2; j <= i2; j++) {
                        int b    = j - l2 + 1;
                        int cost = labels1.get(i).equals(labels2.get(j)) ? 0 : 1;

                        if (lm1[i] == l1 && lm2[j] == l2) {
                            // ---- Main-path case ----
                            // Both i and j lie on the leftmost path of their
                            // respective keyroot subtrees → direct DP transition.
                            fd[a][b] = min3(
                                fd[a - 1][b] + 1,      // delete node i
                                fd[a][b - 1] + 1,      // insert node j
                                fd[a - 1][b - 1] + cost // rename / match
                            );
                            td[i][j] = fd[a][b];

                        } else {
                            // ---- Off-path case ----
                            // At least one node is NOT on the leftmost path.
                            // Use the already-computed td[i][j] for their subtrees
                            // (guaranteed to be set by an earlier keyroot pair —
                            //  see Zhang-Shasha §3 for the correctness argument).
                            int la = lm1[i] - l1; // offset of i's leftmost leaf in fd
                            int lb = lm2[j] - l2;
                            fd[a][b] = min3(
                                fd[a - 1][b] + 1,
                                fd[a][b - 1] + 1,
                                fd[la][lb] + td[i][j]
                            );
                        }
                    }
                }
            }
        }

        return td[n1 - 1][n2 - 1];
    }

    /**
     * Normalised TED similarity: 1 − TED / max(|T1|, |T2|).
     * Returns 1.0 for identical trees, approaching 0.0 for completely different trees.
     */
    public static double normalizedSimilarity(TedNode t1, TedNode t2) {
        int dist    = compute(t1, t2);
        int maxSize = Math.max(t1.size(), t2.size());
        if (maxSize == 0) return 1.0;
        return Math.max(0.0, 1.0 - (double) dist / maxSize);
    }

    // -----------------------------------------------------------------------
    // Post-order traversal
    // -----------------------------------------------------------------------

    /**
     * Append all nodes of {@code node}'s subtree to {@code labels} and {@code lmd}
     * in post-order.  Returns the post-order index of {@code node}.
     *
     * <p>{@code lmd[i]} = the post-order index of the leftmost leaf descendant
     * of the i-th node.  For a leaf this equals the node's own index.
     */
    private static int postOrder(TedNode node,
                                  List<String>  labels,
                                  List<Integer> lmd) {
        if (node.children.isEmpty()) {
            int idx = labels.size();
            labels.add(node.label);
            lmd.add(idx);        // leaf: leftmost descendant is itself
            return idx;
        }

        // Internal node: recurse into children first (post-order)
        int leftmostOfFirstChild = -1;
        for (int ci = 0; ci < node.children.size(); ci++) {
            int childIdx = postOrder(node.children.get(ci), labels, lmd);
            if (ci == 0) leftmostOfFirstChild = lmd.get(childIdx);
        }

        int idx = labels.size();
        labels.add(node.label);
        lmd.add(leftmostOfFirstChild); // inherit leftmost leaf from first child
        return idx;
    }

    // -----------------------------------------------------------------------
    // Keyroot computation
    // -----------------------------------------------------------------------

    /**
     * Compute keyroots for tree T represented by its post-order leftmost-leaf array.
     *
     * <p>A keyroot is a node k such that no node with a higher post-order index
     * shares the same leftmost-leaf value.  Equivalently: for each distinct lmd
     * value, keep only the rightmost (highest post-order index) node.
     *
     * @return post-order indices of keyroots, sorted in increasing order
     */
    private static int[] keyroots(int[] lm, int n) {
        // lm values are valid node indices (0..n-1), so use them as array indices.
        int[] best = new int[n];
        Arrays.fill(best, -1);
        for (int i = 0; i < n; i++) {
            best[lm[i]] = i; // overwrite → keeps the highest post-order index
        }
        List<Integer> kr = new ArrayList<>();
        for (int v : best) if (v >= 0) kr.add(v);
        kr.sort(Integer::compareTo);
        return kr.stream().mapToInt(x -> x).toArray();
    }

    // -----------------------------------------------------------------------
    // Utilities
    // -----------------------------------------------------------------------

    private static int min3(int a, int b, int c) {
        return Math.min(a, Math.min(b, c));
    }
}
