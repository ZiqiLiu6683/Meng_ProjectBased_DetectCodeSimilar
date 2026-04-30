// Ziqi Liu Meng Project-Based Software Engineering
// API call similarity – Strategy 5 (file-level, CLAN-inspired).
//
// Design rationale (from McMillan et al. ICSE 2012 – CLAN):
//   CLAN treats an application's API usage as a *bag of API identifiers*,
//   not as ordered sequences.  It builds a Term-Document Matrix (rows =
//   JDK package/class names, columns = applications) and computes cosine
//   similarity via LSI.  The fundamental unit of comparison is the entire
//   file (application), not individual methods.
//
//   We adopt the same philosophy with a lighter-weight implementation:
//   collect every EXTERNAL MethodCallExpr name from the whole
//   CompilationUnit into a Set<String>, then compute Jaccard overlap
//   between the two sets.  This mirrors Strategy 2 (subtree Jaccard) but
//   operates on the "API vocabulary" of a class rather than its tree structure.
//
// Division of labour with S1-S4:
//   S1  file-level token Winnowing   (surface syntax)
//   S2  file-level subtree Jaccard   (exact structural overlap)
//   S3  method-level token Winnowing (internal call/body syntax)
//   S4  method-level TED / APTED     (structural near-matches)
//   S5  file-level API call Jaccard  (external library usage vocabulary)
//
// What S5 captures that S1-S4 miss:
//   Two files may differ completely in variable names, loop style, or
//   control-flow structure yet call the same external libraries in the
//   same combination (e.g. both use Collections.sort + Iterator.hasNext +
//   List.add).  S5 surfaces this "API fingerprint" independently of syntax.
//
// Internal vs External:
//   "Internal" calls are calls to methods declared within the same
//   CompilationUnit.  They are already handled by S3/S4 and would inject
//   project-specific noise into the API vocabulary; we filter them out.
//   Only calls whose name does NOT appear in the file's own MethodDeclaration
//   name set are kept.
package com.ziqi.codesim.ast;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.ziqi.codesim.sim.Similarity;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class ApiCallSimilarity {

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Extract the set of external API call names from an entire CompilationUnit.
     * "External" means the callee name is NOT declared as a method in this file.
     * Only the callee name is kept (scope/receiver discarded) for robustness
     * to variable renaming (e.g. list.add == myList.add == "add").
     */
    public static Set<String> extractApiCallSet(CompilationUnit cu) {
        // All method names defined in this file → "internal"
        Set<String> internalNames = cu.findAll(MethodDeclaration.class).stream()
            .map(MethodDeclaration::getNameAsString)
            .collect(Collectors.toSet());

        Set<String> apiCalls = new HashSet<>();
        cu.findAll(MethodCallExpr.class).forEach(call -> {
            String name = call.getNameAsString();
            if (!internalNames.contains(name)) {
                apiCalls.add(name);
            }
        });
        return apiCalls;
    }

    /**
     * Sentinel value returned by {@link #compute} when S5 is NOT_APPLICABLE
     * (i.e. union(apiA, apiB) is empty — both files have zero external API calls).
     * The caller (AstMain) must check for this value and exclude S5 from the
     * combined score rather than treating it as 0% similarity.
     */
    public static final double NOT_APPLICABLE = -1.0;

    /**
     * Compute file-level API call similarity between two compilation units.
     * Returns the Jaccard index of their external API call name sets, or
     * {@link #NOT_APPLICABLE} when union(apiA, apiB) is empty.
     *
     * Edge cases:
     *   Both empty  → NOT_APPLICABLE (-1.0): no API vocabulary at all; exclude
     *                 from combined score (common for BCB single-method fragments
     *                 and pure-algorithm code with no external library calls).
     *   One empty   → 0.0 (Jaccard naturally: intersection=0, union>0).
     *                 This IS a real signal — the files use completely different
     *                 API families — so it IS included in the combined score.
     *   Both non-empty → normal Jaccard.
     */
    public static double compute(CompilationUnit cuA, CompilationUnit cuB) {
        Set<String> apiA = extractApiCallSet(cuA);
        Set<String> apiB = extractApiCallSet(cuB);

        // union empty ↔ both empty: no signal at all
        if (apiA.isEmpty() && apiB.isEmpty()) return NOT_APPLICABLE;

        return Similarity.jaccard(apiA, apiB);
    }
}
