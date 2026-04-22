// Ziqi Liu Meng Project-Based Software Engineering
// Subtree-based structural similarity: extract canonical hashes of all AST subtrees
// using a bottom-up recursive traversal. Each subtree hash encodes the node type
// and the ordered sequence of its children's hashes, so structurally identical
// subtrees (after leaf normalisation) produce the same hash regardless of position.
package com.ziqi.codesim.ast;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.type.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SubtreeHasher {

    // FNV-1a 64-bit offset and prime constants
    private static final long FNV_OFFSET = 0xcbf29ce484222325L;
    private static final long FNV_PRIME  = 0x100000001b3L;

    /**
     * Extract hashes of all subtrees with node-count >= minSize from the given root.
     *
     * @param root    root AST node (typically a CompilationUnit)
     * @param minSize minimum number of AST nodes a subtree must contain to be collected
     *                (recommended: 3 – filters trivial leaf-only subtrees)
     * @return set of 64-bit subtree hashes
     */
    public static Set<Long> extractSubtreeHashes(Node root, int minSize) {
        Set<Long> hashes = new HashSet<>();
        computeHash(root, minSize, hashes);
        return hashes;
    }

    // -----------------------------------------------------------------------
    // Recursive bottom-up hash computation
    // Returns long[2] = { subtreeHash, subtreeSize }
    // -----------------------------------------------------------------------
    private static long[] computeHash(Node node, int minSize, Set<Long> collected) {
        // Step 1: get the canonical label for this node
        String label = normalizeLabel(node);

        // Step 2: hash the label with FNV-1a
        long hash = fnv1a(label);
        int  size = 1;

        // Step 3: incorporate children (ordered – preserves structural shape)
        for (Node child : node.getChildNodes()) {
            long[] childResult = computeHash(child, minSize, collected);
            hash = combineHash(hash, childResult[0]);
            size += (int) childResult[1];
        }

        // Step 4: collect if large enough
        if (size >= minSize) {
            collected.add(hash);
        }

        return new long[]{ hash, size };
    }

    // -----------------------------------------------------------------------
    // Node label normalisation
    // Leaf nodes that carry user-defined values are abstracted into category
    // tokens (same convention as AstTokenizer) so that renamed variables or
    // refactored literals do not break subtree equality.
    // Structural / operator nodes keep their class-simple-name so that the
    // tree shape is faithfully encoded.
    // -----------------------------------------------------------------------
    private static String normalizeLabel(Node n) {
        // --- Literals ---
        if (n instanceof StringLiteralExpr)  return "STR";
        if (n instanceof IntegerLiteralExpr) return "NUM";
        if (n instanceof LongLiteralExpr)    return "NUM";
        if (n instanceof DoubleLiteralExpr)  return "NUM";
        if (n instanceof CharLiteralExpr)    return "CHR";
        if (n instanceof BooleanLiteralExpr) return "BOOL";
        if (n instanceof NullLiteralExpr)    return "NULL";

        // --- Names (user-defined identifiers) ---
        if (n instanceof SimpleName) return "ID";
        if (n instanceof NameExpr)   return "ID";

        // --- Special references ---
        if (n instanceof ThisExpr)  return "THIS";
        if (n instanceof SuperExpr) return "SUPER";

        // --- Primitive types: keep the actual type name (int, boolean, …)
        //     so that type-changing refactors are detected as differences ---
        if (n instanceof PrimitiveType) return "T:" + n.toString();

        // --- All other nodes: use the AST node class name ---
        // e.g. "IfStmt", "ForStmt", "BinaryExpr", "MethodDeclaration", …
        return n.getClass().getSimpleName();
    }

    // -----------------------------------------------------------------------
    // Hash utilities
    // -----------------------------------------------------------------------

    /** FNV-1a 64-bit hash of a string. */
    private static long fnv1a(String s) {
        long h = FNV_OFFSET;
        for (int i = 0; i < s.length(); i++) {
            h ^= s.charAt(i);
            h *= FNV_PRIME;
        }
        return h;
    }

    /**
     * Combine a parent hash with a child hash.
     * Uses a multiply-and-XOR mix so that {A, B} ≠ {B, A} (order-sensitive).
     */
    private static long combineHash(long parent, long child) {
        // Knuth multiplicative hash mix, then XOR child
        return (parent * 0x9e3779b97f4a7c15L) ^ child;
    }
}
