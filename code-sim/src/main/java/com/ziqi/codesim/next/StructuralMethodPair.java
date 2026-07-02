package com.ziqi.codesim.next;

/**
 * Evidence from Phase A that a structural region group aligns two methods (possibly across method
 * boundaries, e.g. helper extraction). Carries raw WALA method signatures and the region's
 * substance-weighted coverage. A cross-method region group contributes one of these for each
 * (left method, right method) it spans.
 *
 * @param leftRawSignature  left method signature (WALA form)
 * @param rightRawSignature right method signature (WALA form)
 * @param coverage          substance-weighted coverage of the aligning region group
 */
public record StructuralMethodPair(String leftRawSignature, String rightRawSignature, double coverage) {
}
