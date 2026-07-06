package com.ziqi.codesim.next.semantic.eval;

/**
 * One labelled file pair for the full-pipeline classification eval. {@code expectedFamily} is the
 * clone family a fair human labeller would assign, collapsing the pipeline's T4 sub-tiers
 * (T4_CONFIRMED / T4_DYNAMIC_EVIDENCE / POSSIBLE_T4_CANDIDATE) into a single {@code "T4"}:
 * one of {@code "T1"}, {@code "T2"}, {@code "T3"}, {@code "T4"}, {@code "NON_CLONE"}.
 *
 * <p>{@code note} records WHY the case is labelled that way (and, for probe cases, that the outcome
 * is genuinely uncertain) -- so a mismatch in the report is read as a finding, not just a red cell.
 */
public record ClassificationCase(String id, String expectedFamily, String note,
                                 String leftSource, String rightSource) {
}
