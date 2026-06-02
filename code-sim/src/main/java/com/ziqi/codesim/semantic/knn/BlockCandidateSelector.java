package com.ziqi.codesim.semantic.knn;

import com.ziqi.codesim.semantic.raw.RawToolBlock;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class BlockCandidateSelector {
    private final RawBlockFeatureExtractor extractor;

    public BlockCandidateSelector() {
        this(new RawBlockFeatureExtractor());
    }

    public BlockCandidateSelector(RawBlockFeatureExtractor extractor) {
        this.extractor = extractor;
    }

    public Map<String, List<BlockCandidate>> select(
            Map<String, RawToolBlock> queryBlocks,
            Map<String, RawToolBlock> candidateBlocks,
            Set<String> selectedChannels,
            KnnFeatureView view,
            int topK) {
        List<RawFeatureVector> rawIndexVectors = new ArrayList<>();
        for (Map.Entry<String, RawToolBlock> entry : candidateBlocks.entrySet()) {
            rawIndexVectors.add(extractor.extract(entry.getKey(), entry.getValue(), selectedChannels, view));
        }
        List<RawFeatureVector> rawQueryVectors = new ArrayList<>();
        for (Map.Entry<String, RawToolBlock> entry : queryBlocks.entrySet()) {
            rawQueryVectors.add(extractor.extract(entry.getKey(), entry.getValue(), selectedChannels, view));
        }
        List<RawFeatureVector> fitVectors = new ArrayList<>(rawIndexVectors);
        fitVectors.addAll(rawQueryVectors);
        FeaturePreprocessor preprocessor = FeaturePreprocessor.fit(fitVectors);

        List<StandardizedFeatureVector> indexVectors = rawIndexVectors.stream()
                .map(preprocessor::transform)
                .toList();
        ExactKnnIndex index = new ExactKnnIndex(indexVectors);

        Map<String, List<BlockCandidate>> results = new LinkedHashMap<>();
        for (RawFeatureVector queryVector : rawQueryVectors) {
            StandardizedFeatureVector transformed = preprocessor.transform(queryVector);
            results.put(queryVector.itemId(), index.query(transformed, topK));
        }
        return results;
    }
}
