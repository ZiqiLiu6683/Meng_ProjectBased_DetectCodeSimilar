package com.ziqi.codesim.pipeline;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.ziqi.codesim.ast.ApiCallSimilarity;
import com.ziqi.codesim.ast.AstTokenizer;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Stage0Profiler {
    private static final int EMPTY_AST_NODE_LIMIT = 5;
    private static final double BALANCED_METHOD_RATIO = 0.60;
    private static final double ASYMMETRIC_FILE_SIZE_RATIO = 0.50;
    private static final int API_DENSE_CALL_COUNT = 5;
    private static final int TRIVIAL_METHOD_NODE_LIMIT = 8;
    private static final int S4_COST_RISK_PAIR_LIMIT = 200;

    public Stage0Profile profile(String sourceA, String sourceB) {
        CompilationUnit cuA = null;
        CompilationUnit cuB = null;
        boolean parseOkA = true;
        boolean parseOkB = true;
        try {
            cuA = AstTokenizer.parse(sourceA);
        } catch (RuntimeException ex) {
            parseOkA = false;
        }
        try {
            cuB = AstTokenizer.parse(sourceB);
        } catch (RuntimeException ex) {
            parseOkB = false;
        }

        if (!parseOkA || !parseOkB) {
            return parseFailed(parseOkA, parseOkB);
        }

        FileProfile a = inspect(cuA);
        FileProfile b = inspect(cuB);
        Set<Stage0Flag> flags = EnumSet.noneOf(Stage0Flag.class);

        if (a.hasClassContext()) flags.add(Stage0Flag.CLASS_CONTEXT_STRONG);
        if (b.hasClassContext()) flags.add(Stage0Flag.CLASS_CONTEXT_STRONG);
        if (!a.hasClassContext() || !b.hasClassContext()) flags.add(Stage0Flag.CLASS_CONTEXT_WEAK);
        if ((a.methodCount() == 0) != (b.methodCount() == 0)) {
            flags.add(Stage0Flag.ASYMMETRIC_METHOD_PRESENCE);
        }
        if (sizeRatio(a.totalAstNodes(), b.totalAstNodes()) < ASYMMETRIC_FILE_SIZE_RATIO) {
            flags.add(Stage0Flag.ASYMMETRIC_FILE_SIZE);
        }
        if (a.apiCallCount() + b.apiCallCount() >= API_DENSE_CALL_COUNT) {
            flags.add(Stage0Flag.API_DENSE);
        }
        if (a.trivialMethodHeavy() || b.trivialMethodHeavy()) {
            flags.add(Stage0Flag.TRIVIAL_METHOD_HEAVY);
        }
        if ((long) a.methodCount() * b.methodCount() > S4_COST_RISK_PAIR_LIMIT) {
            flags.add(Stage0Flag.S4_COST_RISK);
        }

        Stage0Mode mode = determineMode(a, b);
        Map<String, SignalMode> enabledSignals = enabledSignals(mode, flags);

        return new Stage0Profile(
                mode,
                Set.copyOf(flags),
                enabledSignals,
                "JAVA",
                "JAVA",
                true,
                true,
                a.methodCount(),
                b.methodCount(),
                a.classCount(),
                b.classCount(),
                a.totalAstNodes(),
                b.totalAstNodes(),
                a.maxMethodNodes(),
                b.maxMethodNodes(),
                a.medianMethodNodes(),
                b.medianMethodNodes(),
                a.nonMethodTokenCount(),
                b.nonMethodTokenCount(),
                a.apiCallCount(),
                b.apiCallCount(),
                "Stage 0 profile generated from JavaParser AST metadata."
        );
    }

    private static Stage0Profile parseFailed(boolean parseOkA, boolean parseOkB) {
        Map<String, SignalMode> signals = new HashMap<>();
        signals.put("S1", SignalMode.DISABLED);
        signals.put("S2", SignalMode.DISABLED);
        signals.put("S3", SignalMode.DISABLED);
        signals.put("S4", SignalMode.DISABLED);
        signals.put("S5", SignalMode.DISABLED);
        return new Stage0Profile(
                Stage0Mode.PARSE_FAILED,
                Set.of(),
                signals,
                "JAVA",
                "JAVA",
                parseOkA,
                parseOkB,
                0, 0,
                0, 0,
                0, 0,
                0, 0,
                0, 0,
                0, 0,
                0, 0,
                "At least one input failed JavaParser parsing."
        );
    }

    private static FileProfile inspect(CompilationUnit cu) {
        List<MethodDeclaration> methods = cu.findAll(MethodDeclaration.class);
        List<Integer> methodSizes = new ArrayList<>();
        for (MethodDeclaration method : methods) {
            methodSizes.add(countNodes(method));
        }
        int totalAstNodes = countNodes(cu);
        int maxMethodNodes = methodSizes.stream().mapToInt(Integer::intValue).max().orElse(0);
        double medianMethodNodes = median(methodSizes);
        int nonMethodTokenCount = AstTokenizer.tokenizeNonMethod(cu).size();
        int apiCallCount = ApiCallSimilarity.extractApiCallSet(cu).size();
        int classCount = cu.findAll(TypeDeclaration.class).size();
        boolean hasClassContext = nonMethodTokenCount >= 4 || classCount > 1;
        long trivialCount = methodSizes.stream()
                .filter(size -> size <= TRIVIAL_METHOD_NODE_LIMIT)
                .count();
        boolean trivialMethodHeavy = !methodSizes.isEmpty()
                && trivialCount >= Math.max(2, methodSizes.size() / 2);

        return new FileProfile(
                methods.size(),
                classCount,
                totalAstNodes,
                maxMethodNodes,
                medianMethodNodes,
                nonMethodTokenCount,
                apiCallCount,
                hasClassContext,
                trivialMethodHeavy
        );
    }

    private static Stage0Mode determineMode(FileProfile a, FileProfile b) {
        if (a.totalAstNodes() <= EMPTY_AST_NODE_LIMIT && b.totalAstNodes() <= EMPTY_AST_NODE_LIMIT) {
            return Stage0Mode.EMPTY_OR_TOO_SMALL;
        }
        if (a.methodCount() == 0 || b.methodCount() == 0) {
            return Stage0Mode.NO_METHOD_CLASS_CONTEXT;
        }
        if (a.methodCount() == 1 && b.methodCount() == 1) {
            if (!a.hasClassContext() && !b.hasClassContext()) {
                return Stage0Mode.BCB_SINGLE_METHOD_FRAGMENT;
            }
            return Stage0Mode.SINGLE_METHOD_REAL_FILE;
        }
        if ((a.methodCount() == 1) != (b.methodCount() == 1)) {
            return Stage0Mode.ONE_TO_MANY_METHOD;
        }
        double ratio = sizeRatio(a.methodCount(), b.methodCount());
        return ratio >= BALANCED_METHOD_RATIO
                ? Stage0Mode.MULTI_METHOD_BALANCED
                : Stage0Mode.MULTI_METHOD_UNBALANCED;
    }

    private static Map<String, SignalMode> enabledSignals(Stage0Mode mode, Set<Stage0Flag> flags) {
        Map<String, SignalMode> signals = new HashMap<>();
        signals.put("S1", SignalMode.ENABLED);
        signals.put("S2", SignalMode.ENABLED);
        signals.put("S3", SignalMode.ENABLED);
        signals.put("S4", SignalMode.ENABLED);
        signals.put("S5", SignalMode.ENABLED_IF_APPLICABLE);

        if (mode == Stage0Mode.NO_METHOD_CLASS_CONTEXT) {
            signals.put("S2", SignalMode.DISABLED);
            signals.put("S3", SignalMode.DISABLED);
            signals.put("S4", SignalMode.DISABLED);
        }
        if (mode == Stage0Mode.EMPTY_OR_TOO_SMALL) {
            signals.replaceAll((key, ignored) -> SignalMode.DISABLED);
        }
        if (mode == Stage0Mode.BCB_SINGLE_METHOD_FRAGMENT
                || flags.contains(Stage0Flag.CLASS_CONTEXT_WEAK)) {
            signals.put("S1", SignalMode.ENABLED_WEAK);
        }
        return Map.copyOf(signals);
    }

    private static int countNodes(Node node) {
        int count = 1;
        for (Node child : node.getChildNodes()) {
            count += countNodes(child);
        }
        return count;
    }

    private static double median(List<Integer> values) {
        if (values.isEmpty()) return 0.0;
        List<Integer> sorted = values.stream().sorted().toList();
        int mid = sorted.size() / 2;
        if (sorted.size() % 2 == 1) {
            return sorted.get(mid);
        }
        return (sorted.get(mid - 1) + sorted.get(mid)) / 2.0;
    }

    private static double sizeRatio(int a, int b) {
        int max = Math.max(a, b);
        if (max == 0) return 1.0;
        return (double) Math.min(a, b) / max;
    }

    private record FileProfile(
            int methodCount,
            int classCount,
            int totalAstNodes,
            int maxMethodNodes,
            double medianMethodNodes,
            int nonMethodTokenCount,
            int apiCallCount,
            boolean hasClassContext,
            boolean trivialMethodHeavy
    ) {
    }
}
