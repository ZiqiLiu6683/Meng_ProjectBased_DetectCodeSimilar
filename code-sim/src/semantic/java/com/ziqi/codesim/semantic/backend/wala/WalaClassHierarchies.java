package com.ziqi.codesim.semantic.backend.wala;

import com.ibm.wala.core.util.config.AnalysisScopeReader;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ipa.cha.ClassHierarchyFactory;
import com.ziqi.codesim.semantic.backend.AnalysisException;

import java.nio.file.Path;

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
        try {
            AnalysisScope scope = AnalysisScopeReader.instance.makeJavaBinaryAnalysisScope(
                    input.toAbsolutePath().toString(),
                    null
            );
            return ClassHierarchyFactory.makeWithPhantom(scope);
        } catch (Exception ex) {
            throw new AnalysisException("Failed to build WALA class hierarchy for: " + input, ex);
        }
    }
}
