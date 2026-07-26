package com.ziqi.codesim.pipeline;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.ziqi.codesim.ast.ApiCallSimilarity;
import com.ziqi.codesim.ast.AptedSimilarity;
import com.ziqi.codesim.ast.AstTokenizer;
import com.ziqi.codesim.ast.SubtreeHasher;
import com.ziqi.codesim.fingerprint.Winnowing;
import com.ziqi.codesim.sim.Similarity;
import eu.mihosoft.ext.apted.costmodel.StringUnitCostModel;
import eu.mihosoft.ext.apted.distance.APTED;
import eu.mihosoft.ext.apted.node.StringNodeData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class Stage1Measurement {
    private static final int K_FILE = 6;
    private static final int W_FILE = 5;
    private static final int K_METHOD = 4;
    private static final int W_METHOD = 3;
    private static final int MIN_SUBTREE_SIZE = 3;

    public Stage1Result compute(String sourceA, String sourceB) {
        CompilationUnit cuA = AstTokenizer.parse(sourceA);
        CompilationUnit cuB = AstTokenizer.parse(sourceB);
        return compute(sourceA, sourceB, cuA, cuB);
    }

    public Stage1Result compute(String sourceA, String sourceB,
                                CompilationUnit cuA, CompilationUnit cuB) {
        double s1 = computeS1(cuA, cuB);
        double s5 = ApiCallSimilarity.compute(cuA, cuB);
        SignalStatus s5Status = (s5 == ApiCallSimilarity.NOT_APPLICABLE)
                ? SignalStatus.NOT_APPLICABLE
                : SignalStatus.APPLICABLE;

        List<MethodWork> methodsA = extractMethods(cuA);
        List<MethodWork> methodsB = extractMethods(cuB);
        List<MethodPairRawScore> pairMatrix = new ArrayList<>();

        for (MethodWork a : methodsA) {
            for (MethodWork b : methodsB) {
                pairMatrix.add(scorePair(a, b));
            }
        }

        return new Stage1Result(
                s1,
                s5Status,
                s5,
                normalizeForExactMatch(sourceA).equals(normalizeForExactMatch(sourceB)),
                methodsA.stream().map(MethodWork::descriptor).toList(),
                methodsB.stream().map(MethodWork::descriptor).toList(),
                pairMatrix
        );
    }

    private static double computeS1(CompilationUnit cuA, CompilationUnit cuB) {
        List<String> tokensA = AstTokenizer.tokenizeNonMethod(cuA);
        List<String> tokensB = AstTokenizer.tokenizeNonMethod(cuB);
        List<Winnowing.Fingerprint> fpA = Winnowing.fingerprintTokens(tokensA, K_FILE, W_FILE);
        List<Winnowing.Fingerprint> fpB = Winnowing.fingerprintTokens(tokensB, K_FILE, W_FILE);
        return Similarity.jaccard(fpA, fpB);
    }

    private static List<MethodWork> extractMethods(CompilationUnit cu) {
        List<MethodWork> methods = new ArrayList<>();
        Map<String, Integer> occurrences = new HashMap<>();
        for (MethodDeclaration md : cu.findAll(MethodDeclaration.class)) {
            String declaringType = md.findAncestor(TypeDeclaration.class)
                    .map(TypeDeclaration::getNameAsString)
                    .orElse("(anonymous)");
            String signature = signatureOf(md);
            String occurrenceKey = declaringType + "#" + signature;
            int occurrenceIndex = occurrences.merge(occurrenceKey, 1, Integer::sum);
            String methodId = occurrenceKey + "#" + occurrenceIndex;
            String displayName = declaringType + "." + signature;

            List<String> tokens = AstTokenizer.tokenize(md);
            List<Winnowing.Fingerprint> fingerprints =
                    Winnowing.fingerprintTokens(tokens, K_METHOD, W_METHOD);
            Set<Long> fingerprintHashes = toHashSet(fingerprints);
            Set<Long> subtreeHashes =
                    SubtreeHasher.extractSubtreeHashes(md, MIN_SUBTREE_SIZE);
            eu.mihosoft.ext.apted.node.Node<StringNodeData> aptedTree =
                    AptedSimilarity.toAptedNode(md);
            int treeSize = countAptedNodes(aptedTree);

            MethodDescriptor descriptor = new MethodDescriptor(
                    methodId,
                    displayName,
                    md.getNameAsString(),
                    signature,
                    declaringType,
                    occurrenceIndex,
                    tokens.size(),
                    treeSize
            );
            methods.add(new MethodWork(
                    descriptor,
                    fingerprintHashes,
                    subtreeHashes,
                    aptedTree
            ));
        }
        return methods;
    }

    private static MethodPairRawScore scorePair(MethodWork a, MethodWork b) {
        int intersectionCount = intersectionSize(a.fingerprintHashes(), b.fingerprintHashes());
        double s2 = Similarity.jaccard(a.subtreeHashes(), b.subtreeHashes());
        double s3 = jaccardFromCounts(
                intersectionCount,
                a.fingerprintHashes().size(),
                b.fingerprintHashes().size()
        );

        int tedDistance = -1;
        double s4 = 0.0;
        SignalStatus s4Status = SignalStatus.COMPUTED;
        try {
            tedDistance = computeAptedDistance(a.aptedTree(), b.aptedTree());
            int maxSize = Math.max(a.descriptor().treeSize(), b.descriptor().treeSize());
            s4 = (maxSize == 0) ? 1.0
                    : Math.max(0.0, 1.0 - (double) tedDistance / maxSize);
        } catch (RuntimeException ex) {
            s4Status = SignalStatus.FAILED;
        }

        return new MethodPairRawScore(
                a.descriptor().methodId(),
                b.descriptor().methodId(),
                a.descriptor().treeSize(),
                b.descriptor().treeSize(),
                s2,
                s3,
                s4,
                s4Status,
                tedDistance,
                intersectionCount,
                a.fingerprintHashes().size(),
                b.fingerprintHashes().size()
        );
    }

    private static String signatureOf(MethodDeclaration md) {
        String params = md.getParameters().stream()
                .map(p -> p.getType().asString())
                .collect(Collectors.joining(","));
        return md.getNameAsString() + "(" + params + ")";
    }

    private static Set<Long> toHashSet(List<Winnowing.Fingerprint> fingerprints) {
        Set<Long> hashes = new HashSet<>();
        for (Winnowing.Fingerprint fp : fingerprints) {
            hashes.add(fp.hash);
        }
        return hashes;
    }

    private static int intersectionSize(Set<Long> a, Set<Long> b) {
        Set<Long> intersection = new HashSet<>(a);
        intersection.retainAll(b);
        return intersection.size();
    }

    private static double jaccardFromCounts(int intersection, int countA, int countB) {
        if (countA == 0 && countB == 0) return 1.0;
        int union = countA + countB - intersection;
        return union == 0 ? 0.0 : (double) intersection / union;
    }

    private static int computeAptedDistance(
            eu.mihosoft.ext.apted.node.Node<StringNodeData> a,
            eu.mihosoft.ext.apted.node.Node<StringNodeData> b) {
        APTED<StringUnitCostModel, StringNodeData> apted =
                new APTED<>(new StringUnitCostModel());
        return Math.round(apted.computeEditDistance(a, b));
    }

    private static int countAptedNodes(eu.mihosoft.ext.apted.node.Node<StringNodeData> node) {
        int count = 1;
        for (eu.mihosoft.ext.apted.node.Node<StringNodeData> child : node.getChildren()) {
            count += countAptedNodes(child);
        }
        return count;
    }

    private static String normalizeForExactMatch(String source) {
        StringBuilder out = new StringBuilder();
        boolean inString = false;
        boolean inChar = false;
        boolean inLineComment = false;
        boolean inBlockComment = false;
        boolean escaped = false;

        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
            char next = (i + 1 < source.length()) ? source.charAt(i + 1) : '\0';

            if (inLineComment) {
                if (c == '\n' || c == '\r') {
                    inLineComment = false;
                }
                continue;
            }
            if (inBlockComment) {
                if (c == '*' && next == '/') {
                    inBlockComment = false;
                    i++;
                }
                continue;
            }
            if (!inString && !inChar && c == '/' && next == '/') {
                inLineComment = true;
                i++;
                continue;
            }
            if (!inString && !inChar && c == '/' && next == '*') {
                inBlockComment = true;
                i++;
                continue;
            }
            if (!inString && !inChar && Character.isWhitespace(c)) {
                continue;
            }

            out.append(c);

            if (escaped) {
                escaped = false;
                continue;
            }
            if ((inString || inChar) && c == '\\') {
                escaped = true;
                continue;
            }
            if (!inChar && c == '"') {
                inString = !inString;
            } else if (!inString && c == '\'') {
                inChar = !inChar;
            }
        }
        return out.toString();
    }

    private record MethodWork(
            MethodDescriptor descriptor,
            Set<Long> fingerprintHashes,
            Set<Long> subtreeHashes,
            eu.mihosoft.ext.apted.node.Node<StringNodeData> aptedTree
    ) {
    }
}
