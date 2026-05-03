package com.ziqi.codesim.ast;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.type.PrimitiveType;

import java.util.HashSet;
import java.util.Set;

public class SubtreeHasher {

    private static final long FNV_OFFSET = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;

    public static Set<Long> extractSubtreeHashes(Node root, int minSize) {
        Set<Long> hashes = new HashSet<>();
        computeHash(root, minSize, hashes);
        return hashes;
    }

    private static long[] computeHash(Node node, int minSize, Set<Long> collected) {
        long hash = fnv1a(normalizeLabel(node));
        int size = 1;

        for (Node child : node.getChildNodes()) {
            long[] childResult = computeHash(child, minSize, collected);
            hash = combineHash(hash, childResult[0]);
            size += (int) childResult[1];
        }

        if (size >= minSize) {
            collected.add(hash);
        }

        return new long[]{ hash, size };
    }

    private static String normalizeLabel(Node n) {
        if (n instanceof StringLiteralExpr) return "STR";
        if (n instanceof IntegerLiteralExpr) return "NUM";
        if (n instanceof LongLiteralExpr) return "NUM";
        if (n instanceof DoubleLiteralExpr) return "NUM";
        if (n instanceof CharLiteralExpr) return "CHR";
        if (n instanceof BooleanLiteralExpr) return "BOOL";
        if (n instanceof NullLiteralExpr) return "NULL";
        if (n instanceof SimpleName) return "ID";
        if (n instanceof NameExpr) return "ID";
        if (n instanceof ThisExpr) return "THIS";
        if (n instanceof SuperExpr) return "SUPER";
        if (n instanceof PrimitiveType) return "T:" + n;
        return n.getClass().getSimpleName();
    }

    private static long fnv1a(String s) {
        long h = FNV_OFFSET;
        for (int i = 0; i < s.length(); i++) {
            h ^= s.charAt(i);
            h *= FNV_PRIME;
        }
        return h;
    }

    private static long combineHash(long parent, long child) {
        return (parent * 0x9e3779b97f4a7c15L) ^ child;
    }
}
