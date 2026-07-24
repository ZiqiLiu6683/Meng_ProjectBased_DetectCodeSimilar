package com.ziqi.codesim.semantic.backend.wala;

import com.ibm.wala.core.util.config.AnalysisScopeReader;
import com.ibm.wala.classLoader.BinaryDirectoryTreeModule;
import com.ibm.wala.classLoader.JarFileModule;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ipa.cha.ClassHierarchyFactory;
import com.ibm.wala.types.ClassLoaderReference;
import com.ziqi.codesim.semantic.backend.AnalysisException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarFile;

/**
 * Builds a WALA {@link ClassHierarchy} from a directory of compiled classes.
 *
 * <p>Extracted so the raw-snapshot extractor and the structural analysis backend can share a single
 * hierarchy (and IR cache) per input instead of each constructing their own -- building the
 * hierarchy is the expensive step, so this halves the WALA work for a file pair.
 */
public final class WalaClassHierarchies {

    private WalaClassHierarchies() {
    }

    public static ClassHierarchy build(Path input) throws AnalysisException {
        return build(input, List.of());
    }

    /**
     * Build a hierarchy where only {@code input} belongs to Application. Resolved project classes,
     * dependency jars, and generated stubs are Extension context: WALA may resolve calls and types
     * through them, but extractors will not emit them as clone candidates.
     */
    public static ClassHierarchy build(Path input, List<Path> supportClasspath) throws AnalysisException {
        try {
            return ClassHierarchyFactory.makeWithPhantom(scope(input, supportClasspath));
        } catch (Exception ex) {
            throw new AnalysisException("Failed to build WALA class hierarchy for: " + input, ex);
        }
    }

    /** Shared scope construction for hierarchy-only and SDG/call-graph consumers. */
    public static AnalysisScope scope(Path input, List<Path> supportClasspath) throws AnalysisException {
        try {
            AnalysisScope scope = AnalysisScopeReader.instance.makeJavaBinaryAnalysisScope(
                    input.toAbsolutePath().toString(), null);
            for (Path entry : supportClasspath) {
                if (Files.isDirectory(entry)) {
                    scope.addToScope(ClassLoaderReference.Extension,
                            new BinaryDirectoryTreeModule(entry.toFile()));
                } else if (Files.isRegularFile(entry) && entry.toString().endsWith(".jar")) {
                    scope.addToScope(ClassLoaderReference.Extension,
                            new JarFileModule(new JarFile(entry.toFile())));
                }
            }
            return scope;
        } catch (Exception ex) {
            throw new AnalysisException("Failed to build WALA analysis scope for: " + input, ex);
        }
    }
}
