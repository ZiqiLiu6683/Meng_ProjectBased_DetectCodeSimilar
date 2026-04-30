package com.ziqi.codesim.ast;

import java.util.List;
import java.util.ArrayList;

import com.github.javaparser.*;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.*;

public class AstTokenizer {
    // Transform Java code to AST unit
    public static CompilationUnit parse(String code) {
        ParserConfiguration config = new ParserConfiguration();
        JavaParser parser = new JavaParser(config);
        ParseResult<CompilationUnit> result = parser.parse(code);
        if (result.getResult().isEmpty()) throw new RuntimeException("Failed to parse code: " + result.getProblems());
        return result.getResult().get();
    }

    // S1 — Tokenize only NON-method-body code (import statements, field
    // declarations, class-level annotations, static initializers, etc.).
    // All MethodDeclaration subtrees are skipped entirely, so S1 is fully
    // decoupled from S3 and captures only the class-level skeleton.
    public static List<String> tokenizeNonMethod(CompilationUnit cu) {
        List<String> tokens = new ArrayList<>();
        for (Node child : cu.getChildNodes()) {
            collectSkippingMethods(child, tokens);
        }
        return tokens;
    }

    // Recursive helper: walk the AST but stop (and skip) at every
    // MethodDeclaration boundary.
    private static void collectSkippingMethods(Node node, List<String> tokens) {
        if (node instanceof MethodDeclaration) {
            return; // drop the entire method body
        }
        tokens.add("N:" + node.getClass().getSimpleName());
        String leaf = normalizeLeafs(node);
        if (leaf != null) tokens.add("L:" + leaf);
        for (Node child : node.getChildNodes()) {
            collectSkippingMethods(child, tokens);
        }
    }

    // Tokenize a full AST node (used by S3 / MethodLevelSimilarity for
    // method-body tokens).
    public static List<String> tokenize(Node node) {
        List<String> tokens = new ArrayList<>();
        tokens.add("N:" + node.getClass().getSimpleName());
        String leaf = normalizeLeafs(node);
        if (leaf != null) tokens.add("L:" + leaf);
        for (Node child : node.getChildNodes()) {
            tokens.addAll(tokenize(child));
        }
        return tokens;
    }

    // Leafs: abstract nodes into categorical tokens
    private static String normalizeLeafs(Node n) {
        if (n instanceof SimpleName) return "ID";
        if (n instanceof NameExpr) return "ID";
        if (n instanceof StringLiteralExpr) return "STR";
        if (n instanceof IntegerLiteralExpr) return "NUM";
        if (n instanceof LongLiteralExpr) return "NUM";
        if (n instanceof DoubleLiteralExpr) return "NUM";
        if (n instanceof CharLiteralExpr) return "CHR";
        if (n instanceof BooleanLiteralExpr) return "BOOL";
        if (n instanceof ThisExpr) return "THIS";
        if (n instanceof SuperExpr) return "SUPER";
        if (n instanceof MethodCallExpr) return "CALL";
        return null;
    }
}