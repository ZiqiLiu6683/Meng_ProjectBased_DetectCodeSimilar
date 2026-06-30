package com.ziqi.codesim.region.model;

import java.util.List;

/**
 * Multi-channel descriptor of one {@link SemanticNode}, used by the seed matcher (M3) to find
 * cross-file candidate node pairs.
 *
 * <p>Channels present in this M2 slice:
 * <ul>
 *   <li><b>lexical</b> — {@link #baseLabel}: the node's name-free operation token (+ kind);</li>
 *   <li><b>structural</b> — {@link #wlHashes}: Weisfeiler-Lehman labels over the SDG, one hash per
 *       radius. {@code wlHashes.get(0)} is the radius-0 label (just the node itself) and
 *       {@code wlHashes.get(r)} folds in the node's r-hop typed-dependence neighborhood. Keeping
 *       every radius (not only the last) lets the matcher compare at multiple structural scales.</li>
 *   <li><b>semantic</b> — {@link #semanticValueHash}: hash of the normalized SSA symbolic
 *       expression the node computes (commutativity + constant/parameter canonicalized). Captures
 *       "what it computes", including constants the structural channel cannot see. 0 when the node
 *       computes no value.</li>
 * </ul>
 *
 * @param nodeId            id of the described node within its {@link SemanticGraph}
 * @param baseLabel         lexical channel value
 * @param wlHashes          structural channel: WL label per radius (index 0 == radius 0)
 * @param semanticValueHash semantic channel: normalized SSA value-expression hash
 */
public record NodeDescriptor(int nodeId, String baseLabel, List<Long> wlHashes, long semanticValueHash) {

    public NodeDescriptor {
        wlHashes = List.copyOf(wlHashes);
    }

    /** The widest-radius WL label (most structural context folded in). */
    public long finalWlHash() {
        return wlHashes.get(wlHashes.size() - 1);
    }

    /** WL label at a given radius; {@code radius} is clamped to the available range. */
    public long wlHashAt(int radius) {
        int clamped = Math.max(0, Math.min(radius, wlHashes.size() - 1));
        return wlHashes.get(clamped);
    }
}
