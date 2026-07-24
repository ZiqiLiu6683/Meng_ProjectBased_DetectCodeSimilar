package com.ziqi.codesim.next.semantic.compilation;

import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;

/** Stable, serializable subset of a javac diagnostic. */
public record CompilerDiagnostic(
        Diagnostic.Kind kind,
        String code,
        long line,
        String message
) {
    static CompilerDiagnostic from(Diagnostic<? extends JavaFileObject> diagnostic) {
        return new CompilerDiagnostic(
                diagnostic.getKind(),
                diagnostic.getCode() == null ? "" : diagnostic.getCode(),
                diagnostic.getLineNumber(),
                diagnostic.getMessage(java.util.Locale.ROOT));
    }
}
