package com.ziqi.codesim.region;

import com.ziqi.codesim.region.descriptor.NodeDescriptorBuilder;
import com.ziqi.codesim.region.grow.RegionGroup;
import com.ziqi.codesim.region.grow.RegionGrower;
import com.ziqi.codesim.region.model.NodeDescriptor;
import com.ziqi.codesim.region.model.SemanticGraph;
import com.ziqi.codesim.region.sdg.SdgBuilder;
import com.ziqi.codesim.region.seed.SeedMatcher;
import com.ziqi.codesim.region.seed.SeedPair;
import com.ziqi.codesim.region.semantic.EquivalenceVerdict;
import com.ziqi.codesim.region.semantic.MethodSummaryExtractor;
import com.ziqi.codesim.region.semantic.SemanticMethodMatch;
import com.ziqi.codesim.region.semantic.SmtEquivalenceChecker;
import com.ziqi.codesim.region.semantic.SymbolicExpression;
import com.ziqi.codesim.semantic.backend.AnalysisException;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Full two-phase clone analysis facade for a source pair, compiling each side once:
 * <ul>
 *   <li><b>Phase A</b> — structural, boundary-free region groups (SDG seed-and-extend);</li>
 *   <li><b>Phase B</b> — semantic method-pair equivalence (SMT), which catches Type-4 clones that
 *       Phase A aligned nothing for (different structure, same behaviour).</li>
 * </ul>
 * The two phases are complementary: a clone shows up structurally, semantically, or both.
 */
public final class CloneAnalyzer {

    private static final Pattern PUBLIC_TYPE = Pattern.compile(
            "public\\s+(?:final\\s+|abstract\\s+|sealed\\s+|non-sealed\\s+|strictfp\\s+)*"
                    + "(?:class|interface|enum|record)\\s+([A-Za-z_$][A-Za-z0-9_$]*)");

    private final SdgBuilder sdgBuilder = new SdgBuilder();
    private final NodeDescriptorBuilder descriptorBuilder = new NodeDescriptorBuilder();
    private final SeedMatcher seedMatcher = new SeedMatcher();
    private final RegionGrower regionGrower = new RegionGrower();
    private final MethodSummaryExtractor summaryExtractor = new MethodSummaryExtractor();
    private final SmtEquivalenceChecker equivalenceChecker = new SmtEquivalenceChecker();

    public Result analyze(String leftSource, String rightSource) throws AnalysisException {
        Path workDir = null;
        try {
            workDir = Files.createTempDirectory("clone-analyzer-");
            Path leftClasses = compile(workDir.resolve("left"), leftSource);
            Path rightClasses = compile(workDir.resolve("right"), rightSource);

            List<RegionGroup> regions = structuralRegions(leftClasses, rightClasses);
            List<SemanticMethodMatch> semanticMatches = semanticMatches(leftClasses, rightClasses);
            return new Result(regions, semanticMatches);
        } catch (IOException ex) {
            throw new AnalysisException("Failed to set up clone-analysis workspace", ex);
        } finally {
            if (workDir != null) {
                deleteQuietly(workDir);
            }
        }
    }

    private List<RegionGroup> structuralRegions(Path leftClasses, Path rightClasses) throws AnalysisException {
        SemanticGraph leftGraph = sdgBuilder.build(leftClasses, "left");
        SemanticGraph rightGraph = sdgBuilder.build(rightClasses, "right");
        Map<Integer, NodeDescriptor> leftDesc = descriptorBuilder.build(leftGraph);
        Map<Integer, NodeDescriptor> rightDesc = descriptorBuilder.build(rightGraph);
        List<SeedPair> seeds = seedMatcher.match(leftDesc, rightDesc);
        return regionGrower.grow(leftGraph, leftDesc, rightGraph, rightDesc, seeds);
    }

    private List<SemanticMethodMatch> semanticMatches(Path leftClasses, Path rightClasses)
            throws AnalysisException {
        Map<String, SymbolicExpression> leftSummaries = summaryExtractor.extractAll(leftClasses);
        Map<String, SymbolicExpression> rightSummaries = summaryExtractor.extractAll(rightClasses);
        List<SemanticMethodMatch> matches = new ArrayList<>();
        for (Map.Entry<String, SymbolicExpression> left : leftSummaries.entrySet()) {
            for (Map.Entry<String, SymbolicExpression> right : rightSummaries.entrySet()) {
                EquivalenceVerdict verdict = equivalenceChecker.check(left.getValue(), right.getValue());
                if (verdict == EquivalenceVerdict.EQUIVALENT) {
                    matches.add(new SemanticMethodMatch(left.getKey(), right.getKey(), verdict));
                }
            }
        }
        return matches;
    }

    private static Path compile(Path root, String source) throws AnalysisException {
        try {
            Path sourceDir = root.resolve("src");
            Path classesDir = root.resolve("classes");
            Files.createDirectories(sourceDir);
            Files.createDirectories(classesDir);
            Matcher matcher = PUBLIC_TYPE.matcher(source);
            String fileName = (matcher.find() ? matcher.group(1) : "Input") + ".java";
            Path sourceFile = sourceDir.resolve(fileName);
            Files.writeString(sourceFile, source, StandardCharsets.UTF_8);
            JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
            if (compiler == null) {
                throw new AnalysisException("Clone analysis requires a JDK compiler");
            }
            int exitCode = compiler.run(null, null, null,
                    "-g", "-d", classesDir.toString(), sourceFile.toString());
            if (exitCode != 0) {
                throw new AnalysisException("Source does not compile standalone: " + sourceFile);
            }
            return classesDir;
        } catch (IOException ex) {
            throw new AnalysisException("Failed to compile source for clone analysis", ex);
        }
    }

    private static void deleteQuietly(Path path) {
        try (var stream = Files.walk(path)) {
            stream.sorted((a, b) -> b.compareTo(a)).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // best-effort cleanup
                }
            });
        } catch (IOException ignored) {
            // best-effort cleanup
        }
    }

    /** Combined structural + semantic findings for a file pair. */
    public record Result(List<RegionGroup> regions, List<SemanticMethodMatch> semanticMatches) {
        public Result {
            regions = List.copyOf(regions);
            semanticMatches = List.copyOf(semanticMatches);
        }
    }
}
