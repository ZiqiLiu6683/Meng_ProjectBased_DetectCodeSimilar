package com.ziqi.codesim.region.model;

/**
 * Source line range a {@link SemanticNode} maps back to, in the original {@code .java} file.
 *
 * <p>The whole boundary-free region selector is only useful if every graph node can be projected
 * back to source. Some SDG statements (parameter, heap, and phi pseudo-nodes) have no source line;
 * those carry {@link #SYNTHETIC}. Lines are 1-based, matching {@code javac}/WALA line numbers.
 */
public record SourceSpan(int beginLine, int endLine) {

    /** A node with no corresponding source line (parameter/heap/phi pseudo-statements). */
    public static final SourceSpan SYNTHETIC = new SourceSpan(-1, -1);

    public SourceSpan {
        if (beginLine > 0 && endLine > 0 && endLine < beginLine) {
            int swap = beginLine;
            beginLine = endLine;
            endLine = swap;
        }
    }

    public static SourceSpan ofLine(int line) {
        if (line <= 0) {
            return SYNTHETIC;
        }
        return new SourceSpan(line, line);
    }

    public boolean isSynthetic() {
        return beginLine < 0 || endLine < 0;
    }

    public boolean isKnown() {
        return !isSynthetic();
    }

    /** Smallest span covering both, ignoring synthetic operands. */
    public SourceSpan union(SourceSpan other) {
        if (this.isSynthetic()) {
            return other;
        }
        if (other.isSynthetic()) {
            return this;
        }
        return new SourceSpan(Math.min(beginLine, other.beginLine), Math.max(endLine, other.endLine));
    }

    @Override
    public String toString() {
        if (isSynthetic()) {
            return "<synthetic>";
        }
        return beginLine == endLine ? ("L" + beginLine) : ("L" + beginLine + "-" + endLine);
    }
}
