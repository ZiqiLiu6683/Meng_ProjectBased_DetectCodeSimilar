package com.ziqi.codesim.ast;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.type.PrimitiveType;
import eu.mihosoft.ext.apted.costmodel.StringUnitCostModel;
import eu.mihosoft.ext.apted.distance.APTED;
import eu.mihosoft.ext.apted.node.StringNodeData;

import java.util.ArrayList;
import java.util.List;

public class AptedSimilarity {

    public static class MethodTreeInfo {
        public final String name;
        public final eu.mihosoft.ext.apted.node.Node<StringNodeData> tree;
        public final int treeSize;

        MethodTreeInfo(String name, eu.mihosoft.ext.apted.node.Node<StringNodeData> tree, int size) {
            this.name = name;
            this.tree = tree;
            this.treeSize = size;
        }
    }

    public static class MatchRecord {
        public final String methodA;
        public final String methodB;
        public final double similarity;
        public final int tedDist;
        public final int sizeA;

        MatchRecord(String a, String b, double sim, int dist, int size) {
            this.methodA = a;
            this.methodB = b;
            this.similarity = sim;
            this.tedDist = dist;
            this.sizeA = size;
        }
    }

    public static class Result {
        public final double similarity;
        public final List<MatchRecord> forwardMatches;
        public final List<MatchRecord> backwardMatches;

        Result(double sim, List<MatchRecord> fwd, List<MatchRecord> bwd) {
            this.similarity = sim;
            this.forwardMatches = fwd;
            this.backwardMatches = bwd;
        }
    }

    public static List<MethodTreeInfo> extractMethods(CompilationUnit cu) {
        List<MethodTreeInfo> methods = new ArrayList<>();
        cu.findAll(MethodDeclaration.class).forEach(md -> {
            eu.mihosoft.ext.apted.node.Node<StringNodeData> tree = toAptedNode(md);
            methods.add(new MethodTreeInfo(md.getNameAsString(), tree, countNodes(tree)));
        });
        return methods;
    }

    public static Result compute(CompilationUnit cuA, CompilationUnit cuB) {
        List<MethodTreeInfo> methodsA = extractMethods(cuA);
        List<MethodTreeInfo> methodsB = extractMethods(cuB);

        if (methodsA.isEmpty() && methodsB.isEmpty()) {
            return new Result(1.0, new ArrayList<>(), new ArrayList<>());
        }
        if (methodsA.isEmpty() || methodsB.isEmpty()) {
            return new Result(0.0, new ArrayList<>(), new ArrayList<>());
        }

        List<MatchRecord> fwd = bestMatchPass(methodsA, methodsB);
        List<MatchRecord> bwd = bestMatchPass(methodsB, methodsA);
        return new Result((weightedAverage(fwd) + weightedAverage(bwd)) / 2.0, fwd, bwd);
    }

    public static eu.mihosoft.ext.apted.node.Node<StringNodeData> toAptedNode(Node javaParserNode) {
        eu.mihosoft.ext.apted.node.Node<StringNodeData> aptedNode =
            new eu.mihosoft.ext.apted.node.Node<>(new StringNodeData(normalizeLabel(javaParserNode)));
        for (Node child : javaParserNode.getChildNodes()) {
            aptedNode.addChild(toAptedNode(child));
        }
        return aptedNode;
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

    private static List<MatchRecord> bestMatchPass(List<MethodTreeInfo> from,
                                                   List<MethodTreeInfo> to) {
        List<MatchRecord> records = new ArrayList<>();
        for (MethodTreeInfo mA : from) {
            double bestSim = -1.0;
            int bestDist = Integer.MAX_VALUE;
            String bestName = "(none)";

            for (MethodTreeInfo mB : to) {
                int dist = computeAptedDist(mA.tree, mB.tree);
                int maxSize = Math.max(mA.treeSize, mB.treeSize);
                double sim = maxSize == 0 ? 1.0 : Math.max(0.0, 1.0 - (double) dist / maxSize);
                if (sim > bestSim) {
                    bestSim = sim;
                    bestDist = dist;
                    bestName = mB.name;
                }
            }
            records.add(new MatchRecord(mA.name, bestName, Math.max(0.0, bestSim), bestDist, mA.treeSize));
        }
        return records;
    }

    private static int computeAptedDist(eu.mihosoft.ext.apted.node.Node<StringNodeData> t1,
                                        eu.mihosoft.ext.apted.node.Node<StringNodeData> t2) {
        APTED<StringUnitCostModel, StringNodeData> apted = new APTED<>(new StringUnitCostModel());
        return Math.round(apted.computeEditDistance(t1, t2));
    }

    private static double weightedAverage(List<MatchRecord> records) {
        double totalWeight = 0;
        double weightedSum = 0;
        for (MatchRecord r : records) {
            weightedSum += r.similarity * r.sizeA;
            totalWeight += r.sizeA;
        }
        return totalWeight == 0 ? 0.0 : weightedSum / totalWeight;
    }

    private static int countNodes(eu.mihosoft.ext.apted.node.Node<StringNodeData> n) {
        int count = 1;
        for (eu.mihosoft.ext.apted.node.Node<StringNodeData> child : n.getChildren()) {
            count += countNodes(child);
        }
        return count;
    }
}
