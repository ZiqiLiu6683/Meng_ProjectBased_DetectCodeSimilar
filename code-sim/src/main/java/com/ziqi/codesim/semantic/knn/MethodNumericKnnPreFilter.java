package com.ziqi.codesim.semantic.knn;

import com.ziqi.codesim.semantic.feature.MethodNumericFeatures;
import com.ziqi.codesim.semantic.feature.SemanticFeatureExtractor;
import com.ziqi.codesim.semantic.model.AnalyzedMethod;
import com.ziqi.codesim.semantic.model.BasicBlockUnit;
import com.ziqi.codesim.semantic.model.InstructionUnit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * discovRE-style function-level numeric pre-filter (paper section III-B).
 *
 * <p>Each method is embedded as a numeric feature vector built from
 * {@link SemanticFeatureExtractor#methodFeatures}, log10-compressed and
 * standardized to mean 0 / standard deviation 1 by {@link FeaturePreprocessor},
 * then the top-k candidate methods are retrieved with an exact Euclidean kNN.
 * This is the cheap first stage of discovRE that narrows the candidate set
 * before the expensive structural CFG (MCS) comparison runs on the survivors.
 *
 * <p>Item ids are namespaced with {@code L:}/{@code R:} prefixes so a left and
 * right method that share the same signature are never treated as the same kNN
 * point (which would otherwise be skipped as a self-match).
 */
public class MethodNumericKnnPreFilter {
    private static final String LEFT_PREFIX = "L:";
    private static final String RIGHT_PREFIX = "R:";

    private final SemanticFeatureExtractor featureExtractor;
    private final KnnIndexFactory indexFactory;

    public MethodNumericKnnPreFilter() {
        this(new SemanticFeatureExtractor());
    }

    public MethodNumericKnnPreFilter(SemanticFeatureExtractor featureExtractor) {
        this(featureExtractor, KnnIndexFactory.EXACT_LINEAR);
    }

    public MethodNumericKnnPreFilter(SemanticFeatureExtractor featureExtractor, KnnIndexFactory indexFactory) {
        this.featureExtractor = featureExtractor;
        this.indexFactory = indexFactory;
    }

    /**
     * For each left method, return up to {@code topK} candidate right methods
     * ranked by numeric-feature closeness. The score is {@code 1 / (1 + distance)}
     * so larger is closer, consistent with the existing candidate scoring.
     */
    public Map<String, List<MethodCandidate>> select(
            List<AnalyzedMethod> leftMethods,
            List<AnalyzedMethod> rightMethods,
            int topK) {
        // Incoming-call counts (call-graph in-degree) are a program-level signal, so they are
        // computed per side over the whole method list rather than from a single method.
        Map<String, Integer> leftIncoming = incomingCallCounts(leftMethods);
        Map<String, Integer> rightIncoming = incomingCallCounts(rightMethods);
        List<RawFeatureVector> leftRaw = new ArrayList<>();
        for (AnalyzedMethod method : leftMethods) {
            leftRaw.add(toVector(LEFT_PREFIX, method, leftIncoming));
        }
        List<RawFeatureVector> rightRaw = new ArrayList<>();
        for (AnalyzedMethod method : rightMethods) {
            rightRaw.add(toVector(RIGHT_PREFIX, method, rightIncoming));
        }

        // Fit normalization statistics over both sides so the standardized space
        // is shared, then index only the right (candidate) methods.
        List<RawFeatureVector> fitVectors = new ArrayList<>(rightRaw);
        fitVectors.addAll(leftRaw);
        FeaturePreprocessor preprocessor = FeaturePreprocessor.fit(fitVectors);

        List<StandardizedFeatureVector> indexVectors = new ArrayList<>();
        for (RawFeatureVector vector : rightRaw) {
            indexVectors.add(preprocessor.transform(vector));
        }
        KnnIndex index = indexFactory.create(indexVectors);

        Map<String, List<MethodCandidate>> results = new LinkedHashMap<>();
        for (int i = 0; i < leftMethods.size(); i++) {
            AnalyzedMethod leftMethod = leftMethods.get(i);
            StandardizedFeatureVector query = preprocessor.transform(leftRaw.get(i));
            List<MethodCandidate> candidates = new ArrayList<>();
            for (BlockCandidate candidate : index.query(query, topK)) {
                String rightSignature = stripPrefix(candidate.candidateBlockId());
                candidates.add(new MethodCandidate(rightSignature, 1.0 / (1.0 + candidate.distance())));
            }
            results.put(leftMethod.signature(), candidates);
        }
        return results;
    }

    private RawFeatureVector toVector(String prefix, AnalyzedMethod method, Map<String, Integer> incomingCalls) {
        MethodNumericFeatures features = featureExtractor.methodFeatures(method);
        Map<String, Double> values = new LinkedHashMap<>();
        values.put("basicBlockCount", (double) features.basicBlockCount());
        values.put("cfgEdgeCount", (double) features.cfgEdgeCount());
        values.put("branchCount", (double) features.branchCount());
        values.put("returnCount", (double) features.returnCount());
        values.put("callCount", (double) features.callCount());
        values.put("internalCallCount", (double) features.internalCallCount());
        values.put("externalCallCount", (double) features.externalCallCount());
        values.put("arithmeticCount", (double) features.arithmeticCount());
        values.put("logicCount", (double) features.logicCount());
        values.put("comparisonCount", (double) features.comparisonCount());
        values.put("assignmentCount", (double) features.assignmentCount());
        values.put("allocationCount", (double) features.allocationCount());
        values.put("fieldAccessCount", (double) features.fieldAccessCount());
        values.put("arrayAccessCount", (double) features.arrayAccessCount());
        values.put("constantCount", (double) features.constantCount());
        values.put("stringReferenceCount", (double) features.stringReferenceCount());
        values.put("instructionCount", (double) features.instructionCount());
        values.put("parameterCount", (double) features.parameterCount());
        values.put("loopComponentCount", (double) features.loopComponentCount());
        values.put("localValueCount", (double) features.localValueCount());
        values.put("incomingCallCount", (double) incomingCalls.getOrDefault(method.signature(), 0));
        return new RawFeatureVector(
                prefix + method.signature(),
                KnnFeatureView.DISCOVRE_NUMERIC,
                values,
                Map.of()
        );
    }

    // Call-graph in-degree per method: how many call sites across this side target each method,
    // derived from each instruction's declared call target signature.
    private static Map<String, Integer> incomingCallCounts(List<AnalyzedMethod> methods) {
        Map<String, Integer> inDegree = new HashMap<>();
        for (AnalyzedMethod method : methods) {
            for (BasicBlockUnit block : method.cfg().blocks()) {
                for (InstructionUnit instruction : block.instructions()) {
                    String target = instruction.callTargetSignature();
                    if (target != null && !target.isEmpty()) {
                        inDegree.merge(target, 1, Integer::sum);
                    }
                }
            }
        }
        return inDegree;
    }

    private static String stripPrefix(String itemId) {
        if (itemId.startsWith(RIGHT_PREFIX) || itemId.startsWith(LEFT_PREFIX)) {
            return itemId.substring(2);
        }
        return itemId;
    }

    public record MethodCandidate(String rightMethodSignature, double score) {
    }
}
