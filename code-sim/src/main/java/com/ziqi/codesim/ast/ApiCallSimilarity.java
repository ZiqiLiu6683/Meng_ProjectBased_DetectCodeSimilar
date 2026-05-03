package com.ziqi.codesim.ast;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.ziqi.codesim.sim.Similarity;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class ApiCallSimilarity {
    public static final double NOT_APPLICABLE = -1.0;

    // External API calls exclude methods declared in the same compilation unit.
    public static Set<String> extractApiCallSet(CompilationUnit cu) {
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

    // Returns NOT_APPLICABLE when neither file has external API calls.
    public static double compute(CompilationUnit cuA, CompilationUnit cuB) {
        Set<String> apiA = extractApiCallSet(cuA);
        Set<String> apiB = extractApiCallSet(cuB);
        if (apiA.isEmpty() && apiB.isEmpty()) return NOT_APPLICABLE;
        return Similarity.jaccard(apiA, apiB);
    }
}
