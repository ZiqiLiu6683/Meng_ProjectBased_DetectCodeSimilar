package com.ziqi.codesim.region;

import com.ziqi.codesim.region.descriptor.NodeDescriptorBuilder;
import com.ziqi.codesim.region.grow.RegionGroup;
import com.ziqi.codesim.region.grow.RegionGrower;
import com.ziqi.codesim.region.model.NodeDescriptor;
import com.ziqi.codesim.region.model.SemanticGraph;
import com.ziqi.codesim.region.sdg.SdgBuilder;
import com.ziqi.codesim.region.seed.SeedMatcher;
import com.ziqi.codesim.region.seed.SeedPair;
import com.ziqi.codesim.semantic.backend.AnalysisException;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Phase A facade: turns a pair of Java source strings into ranked, boundary-free region groups by
 * running the whole selector — compile to bytecode, build each side's SDG, descriptors, cross-file
 * seeds, then seed-and-extend region growth. This is the single public entry point other code (the
 * eval harness, and later the T1/T2/T3 back-end integration) should use.
 *
 * <p>Each source is compiled standalone with {@code -g} (line tables are required for source
 * mapping). Inputs that do not compile in isolation will throw {@link AnalysisException}.
 */
public final class RegionSelector {

    private static final Pattern PUBLIC_TYPE = Pattern.compile(
            "public\\s+(?:final\\s+|abstract\\s+|sealed\\s+|non-sealed\\s+|strictfp\\s+)*"
                    + "(?:class|interface|enum|record)\\s+([A-Za-z_$][A-Za-z0-9_$]*)");

    private final SdgBuilder sdgBuilder;
    private final NodeDescriptorBuilder descriptorBuilder;
    private final SeedMatcher seedMatcher;
    private final RegionGrower regionGrower;

    public RegionSelector() {
        this(new SdgBuilder(), new NodeDescriptorBuilder(), new SeedMatcher(), new RegionGrower());
    }

    public RegionSelector(SdgBuilder sdgBuilder, NodeDescriptorBuilder descriptorBuilder,
                          SeedMatcher seedMatcher, RegionGrower regionGrower) {
        this.sdgBuilder = sdgBuilder;
        this.descriptorBuilder = descriptorBuilder;
        this.seedMatcher = seedMatcher;
        this.regionGrower = regionGrower;
    }

    /** @return region groups linking the two files, ranked by {@link RegionGroup#priority()} desc. */
    public List<RegionGroup> select(String leftSource, String rightSource) throws AnalysisException {
        Path workDir = null;
        try {
            workDir = Files.createTempDirectory("region-selector-");
            Path leftClasses = compile(workDir.resolve("left"), leftSource);
            Path rightClasses = compile(workDir.resolve("right"), rightSource);

            SemanticGraph leftGraph = sdgBuilder.build(leftClasses, "left");
            SemanticGraph rightGraph = sdgBuilder.build(rightClasses, "right");
            Map<Integer, NodeDescriptor> leftDesc = descriptorBuilder.build(leftGraph);
            Map<Integer, NodeDescriptor> rightDesc = descriptorBuilder.build(rightGraph);
            List<SeedPair> seeds = seedMatcher.match(leftDesc, rightDesc);
            return regionGrower.grow(leftGraph, leftDesc, rightGraph, rightDesc, seeds);
        } catch (IOException ex) {
            throw new AnalysisException("Failed to set up region selection workspace", ex);
        } finally {
            if (workDir != null) {
                deleteQuietly(workDir);
            }
        }
    }

    private static Path compile(Path root, String source) throws AnalysisException {
        try {
            Path sourceDir = root.resolve("src");
            Path classesDir = root.resolve("classes");
            Files.createDirectories(sourceDir);
            Files.createDirectories(classesDir);
            Path sourceFile = sourceDir.resolve(publicTypeFileName(source));
            Files.writeString(sourceFile, source, StandardCharsets.UTF_8);
            JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
            if (compiler == null) {
                throw new AnalysisException("Region selector requires a JDK compiler");
            }
            int exitCode = compiler.run(null, null, null,
                    "-g", "-d", classesDir.toString(), sourceFile.toString());
            if (exitCode != 0) {
                throw new AnalysisException("Source does not compile standalone: " + sourceFile);
            }
            return classesDir;
        } catch (IOException ex) {
            throw new AnalysisException("Failed to compile source for region selection", ex);
        }
    }

    private static String publicTypeFileName(String source) {
        Matcher matcher = PUBLIC_TYPE.matcher(source);
        return (matcher.find() ? matcher.group(1) : "Input") + ".java";
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
}
