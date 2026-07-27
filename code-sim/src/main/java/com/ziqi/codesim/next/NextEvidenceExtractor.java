package com.ziqi.codesim.next;

import com.github.javaparser.JavaToken;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.ziqi.codesim.ast.AstTokenizer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class NextEvidenceExtractor {
    private static final int MAX_STATEMENT_WINDOW_SIZE = 6;
    // Per-size budget instead of one shared budget: previously the smallest windows consumed
    // the whole budget first, starving larger windows in big methods (so >5-statement fragment
    // clones inside large methods were missed). Capping per size keeps both small (fine-grained)
    // and large windows. Larger methods still bounded to control region explosion.
    private static final int MAX_WINDOWS_PER_SIZE = 64;

    private static final Set<String> JAVA_KEYWORDS = Set.of(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch",
            "char", "class", "const", "continue", "default", "do", "double",
            "else", "enum", "extends", "final", "finally", "float", "for",
            "goto", "if", "implements", "import", "instanceof", "int",
            "interface", "long", "native", "new", "package", "private",
            "protected", "public", "return", "short", "static", "strictfp",
            "super", "switch", "synchronized", "this", "throw", "throws",
            "transient", "try", "void", "volatile", "while", "var", "record"
    );

    public EvidencePackage extract(String leftSource, String rightSource) {
        CompilationUnit left = AstTokenizer.parse(leftSource);
        CompilationUnit right = AstTokenizer.parse(rightSource);
        return new EvidencePackage(
                regions(left, RegionSide.LEFT),
                regions(right, RegionSide.RIGHT)
        );
    }

    private static List<CodeRegion> regions(CompilationUnit cu, RegionSide side) {
        List<CodeRegion> regions = new ArrayList<>();
        regions.add(region(side.name().toLowerCase() + ":file", side, RegionKind.FILE,
                "(file)", cu));

        Map<String, Integer> occurrence = new HashMap<>();
        for (MethodDeclaration method : cu.findAll(MethodDeclaration.class)) {
            String declaringType = method.findAncestor(TypeDeclaration.class)
                    .map(TypeDeclaration::getNameAsString)
                    .orElse("(anonymous)");
            String signature = signatureOf(method);
            String key = declaringType + "." + signature;
            int index = occurrence.merge(key, 1, Integer::sum);
            String idPrefix = side.name().toLowerCase() + ":method:" + key + "#" + index;
            String displayName = key;
            regions.add(region(idPrefix, side, RegionKind.METHOD, displayName, method));
            method.getBody().ifPresent(body -> {
                regions.add(region(
                        idPrefix + ":body",
                        side,
                        RegionKind.METHOD_BODY_REGION,
                        displayName + " body",
                        body
                ));
                addStatementWindowRegions(regions, idPrefix, side, displayName, method);
                addBlockSequenceRegions(regions, idPrefix, side, displayName, method);
                addControlRegions(regions, idPrefix, side, displayName, method);
            });
        }
        return List.copyOf(regions);
    }

    private static void addStatementWindowRegions(List<CodeRegion> regions,
                                                  String idPrefix,
                                                  RegionSide side,
                                                  String displayName,
                                                  MethodDeclaration method) {
        List<Statement> statements = nonBlockStatements(method);
        for (int size = 2; size <= Math.min(MAX_STATEMENT_WINDOW_SIZE, statements.size()); size++) {
            int createdThisSize = 0;
            for (int start = 0; start + size <= statements.size(); start++) {
                if (createdThisSize >= MAX_WINDOWS_PER_SIZE) {
                    break;
                }
                createdThisSize++;
                List<Statement> window = statements.subList(start, start + size);
                regions.add(regionFromStatements(
                        idPrefix + ":stmt-window:" + start + ":" + size,
                        side,
                        RegionKind.STATEMENT_WINDOW_REGION,
                        displayName + " statements " + (start + 1) + "-" + (start + size),
                        window
                ));
            }
        }
    }

    private static void addBlockSequenceRegions(List<CodeRegion> regions,
                                                String idPrefix,
                                                RegionSide side,
                                                String displayName,
                                                MethodDeclaration method) {
        List<BlockStmt> blocks = method.findAll(BlockStmt.class);
        int blockIndex = 0;
        for (BlockStmt block : blocks) {
            List<Statement> statements = block.getStatements().stream()
                    .filter(s -> !(s instanceof BlockStmt))
                    .toList();
            if (statements.size() < 2) {
                blockIndex++;
                continue;
            }
            regions.add(regionFromStatements(
                    idPrefix + ":block-sequence:" + blockIndex,
                    side,
                    RegionKind.BLOCK_SEQUENCE_REGION,
                    displayName + " block sequence " + blockIndex,
                    statements
            ));
            blockIndex++;
        }
    }

    private static void addControlRegions(List<CodeRegion> regions,
                                          String idPrefix,
                                          RegionSide side,
                                          String displayName,
                                          MethodDeclaration method) {
        int index = 0;
        for (Statement statement : nonBlockStatements(method)) {
            if (!isControlStatement(statement)) {
                continue;
            }
            regions.add(region(
                    idPrefix + ":control:" + index,
                    side,
                    RegionKind.CONTROL_REGION,
                    displayName + " " + statement.getClass().getSimpleName() + " " + index,
                    statement
            ));
            index++;
        }
    }

    private static CodeRegion region(String id, RegionSide side, RegionKind kind,
                                     String displayName, Node node) {
        List<String> rawTokens = tokens(node, TokenView.RAW);
        List<String> t1Tokens = tokens(node, TokenView.T1);
        List<String> t2Tokens = tokens(node, TokenView.T2);
        List<Statement> statements = node.findAll(Statement.class).stream()
                .filter(s -> !(s instanceof BlockStmt))
                .toList();
        List<String> statementTexts = statements.stream()
                .map(s -> String.join(" ", tokens(s, TokenView.T1)))
                .filter(s -> !s.isBlank())
                .toList();
        List<String> normalizedStatementTexts = statements.stream()
                .map(s -> String.join(" ", tokens(s, TokenView.T2)))
                .filter(s -> !s.isBlank())
                .toList();

        int beginLine = node.getRange().map(r -> r.begin.line).orElse(-1);
        int endLine = node.getRange().map(r -> r.end.line).orElse(-1);
        return new CodeRegion(
                id,
                side,
                kind,
                displayName,
                beginLine,
                endLine,
                rawTokens,
                t1Tokens,
                t2Tokens,
                statementTexts,
                normalizedStatementTexts
        );
    }

    private static CodeRegion regionFromStatements(String id,
                                                   RegionSide side,
                                                   RegionKind kind,
                                                   String displayName,
                                                   List<Statement> statements) {
        List<String> rawTokens = new ArrayList<>();
        List<String> t1Tokens = new ArrayList<>();
        List<String> t2Tokens = new ArrayList<>();
        List<String> statementTexts = new ArrayList<>();
        List<String> normalizedStatementTexts = new ArrayList<>();
        for (Statement statement : statements) {
            rawTokens.addAll(tokens(statement, TokenView.RAW));
            t1Tokens.addAll(tokens(statement, TokenView.T1));
            t2Tokens.addAll(tokens(statement, TokenView.T2));
            String statementText = String.join(" ", tokens(statement, TokenView.T1));
            if (!statementText.isBlank()) {
                statementTexts.add(statementText);
            }
            String normalizedStatementText = String.join(" ", tokens(statement, TokenView.T2));
            if (!normalizedStatementText.isBlank()) {
                normalizedStatementTexts.add(normalizedStatementText);
            }
        }
        int beginLine = statements.stream()
                .flatMap(s -> s.getRange().stream())
                .mapToInt(r -> r.begin.line)
                .min()
                .orElse(-1);
        int endLine = statements.stream()
                .flatMap(s -> s.getRange().stream())
                .mapToInt(r -> r.end.line)
                .max()
                .orElse(-1);
        return new CodeRegion(
                id,
                side,
                kind,
                displayName,
                beginLine,
                endLine,
                // A block sequence drops nested blocks, so consecutive statements here can still
                // leave a hole; a statement window is contiguous and simply yields one run.
                segmentsOf(statements),
                List.copyOf(rawTokens),
                List.copyOf(t1Tokens),
                List.copyOf(t2Tokens),
                List.copyOf(statementTexts),
                List.copyOf(normalizedStatementTexts)
        );
    }

    /** The line runs a set of statements occupies, coalesced. */
    private static List<LineSegment> segmentsOf(List<Statement> statements) {
        List<LineSegment> segments = new ArrayList<>();
        for (Statement statement : statements) {
            statement.getRange().ifPresent(range ->
                    segments.add(new LineSegment(range.begin.line, range.end.line)));
        }
        return LineSegment.normalize(segments);
    }

    private static List<Statement> nonBlockStatements(Node node) {
        return node.findAll(Statement.class).stream()
                .filter(s -> !(s instanceof BlockStmt))
                .toList();
    }

    /**
     * Projects a Phase A structural region group back into a classifiable source region: builds a
     * {@link CodeRegion} from the outermost source statements that overlap the given 1-based line
     * numbers. Reuses the same tokenizer/statement machinery the normal regions use, so the
     * recognizer can classify a Phase A region syntactically (T1/T2/T3) with real source tokens.
     */
    public static CodeRegion regionForLines(String source, Set<Integer> lines, RegionSide side,
                                            RegionKind kind, String id, String displayName) {
        CompilationUnit cu = AstTokenizer.parse(source);
        List<Statement> statements = cu.findAll(Statement.class).stream()
                .filter(s -> !(s instanceof BlockStmt))
                .filter(s -> overlapsLines(s, lines))
                .filter(s -> !hasOverlappingStatementAncestor(s, lines))
                .toList();
        return regionFromStatements(id, side, kind, displayName, statements);
    }

    /**
     * Reconstructs a Phase A region into two boundary-free source regions the recognizer can judge
     * syntactically. Each side collects ALL non-block statements its spanned methods cover -- crucially
     * including statements NESTED inside loops/ifs, so an edit inside a loop (e.g. an inserted flag) is
     * visible as its own statement rather than swallowed by the enclosing loop. The two sides are then
     * handed to the UNCHANGED recognizer, whose text-LCS does the actual matching (order-tolerant, so
     * reordered or split statements resolve to T3 naturally -- no forced one-to-one).
     *
     * <p>Because the statements come from the spanned methods (not just the aligned lines), a
     * helper-extracted clone's two methods are compared as one boundary-free unit. When the region
     * crosses methods, a {@code CROSS_METHOD_REGION} marker rides along so the recognizer never lets a
     * similarity number silently drop a known cross-method clone to NON_CLONE.
     *
     * <p>Tokens (T1/T2 exact comparison) are built from the OUTERMOST statements to avoid double
     * counting a loop and its body; statement texts (the T3 edit script) are built from ALL nested
     * statements -- mirroring how ordinary method regions are constructed.
     */
    public static RegionCandidate reconstructAlignedRegion(
            String leftSource, Set<Integer> leftSpanLines,
            String rightSource, Set<Integer> rightSpanLines,
            boolean crossMethod, String id) {
        CodeRegion left = alignedRegion(leftSource, leftSpanLines, id + "L", RegionSide.LEFT);
        CodeRegion right = alignedRegion(rightSource, rightSpanLines, id + "R", RegionSide.RIGHT);
        List<CandidateSource> sources = new ArrayList<>();
        sources.add(new CandidateSource("ALIGNED_REGION_SCAN",
                Math.max(left.normalizedStatementTexts().size(), right.normalizedStatementTexts().size())));
        if (crossMethod) {
            sources.add(new CandidateSource("CROSS_METHOD_REGION", 1.0));
        }
        return new RegionCandidate("AR" + id, left, right, List.copyOf(sources));
    }

    private static CodeRegion alignedRegion(String source, Set<Integer> spanLines, String id, RegionSide side) {
        CompilationUnit cu = AstTokenizer.parse(source);
        // Tokens from outermost statements (no double counting); statement texts from ALL non-block
        // statements including nested ones (so intra-loop edits are visible at statement granularity).
        List<Statement> outermost = outermostStatementsOverlapping(cu, spanLines);
        List<Statement> allStatements = cu.findAll(Statement.class).stream()
                .filter(s -> !(s instanceof BlockStmt))
                .filter(s -> overlapsLines(s, spanLines))
                .toList();

        List<String> rawTokens = new ArrayList<>();
        List<String> t1Tokens = new ArrayList<>();
        List<String> t2Tokens = new ArrayList<>();
        for (Statement statement : outermost) {
            rawTokens.addAll(tokens(statement, TokenView.RAW));
            t1Tokens.addAll(tokens(statement, TokenView.T1));
            t2Tokens.addAll(tokens(statement, TokenView.T2));
        }

        List<String> statementTexts = new ArrayList<>();
        List<String> normalizedStatementTexts = new ArrayList<>();
        for (Statement statement : allStatements) {
            // OWN text (excluding nested statements) so a loop/if contributes only its header, not its
            // whole body -- otherwise the container's full text differs whenever anything inside it
            // changes and dilutes the statement-level LCS.
            String t1 = String.join(" ", ownTokens(statement, TokenView.T1));
            if (!t1.isBlank()) {
                statementTexts.add(t1);
            }
            String t2 = String.join(" ", ownTokens(statement, TokenView.T2));
            if (!t2.isBlank()) {
                normalizedStatementTexts.add(t2);
            }
        }

        int beginLine = outermost.stream().flatMap(s -> s.getRange().stream())
                .mapToInt(r -> r.begin.line).min().orElse(-1);
        int endLine = outermost.stream().flatMap(s -> s.getRange().stream())
                .mapToInt(r -> r.end.line).max().orElse(-1);
        // The runs the tokens above came from. Growth can spread this region across two methods, so
        // beginLine..endLine may enclose code that belongs to neither -- and the clone type is
        // decided on `outermost` alone, never on the enclosed remainder.
        List<LineSegment> segments = segmentsOf(outermost);
        return new CodeRegion(id, side, RegionKind.CALL_EXPANDED_REGION, "aligned region " + id,
                beginLine, endLine, segments,
                List.copyOf(rawTokens), List.copyOf(t1Tokens), List.copyOf(t2Tokens),
                List.copyOf(statementTexts), List.copyOf(normalizedStatementTexts));
    }

    private static List<Statement> outermostStatementsOverlapping(CompilationUnit cu, Set<Integer> lines) {
        return cu.findAll(Statement.class).stream()
                .filter(s -> !(s instanceof BlockStmt))
                .filter(s -> overlapsLines(s, lines))
                .filter(s -> !hasOverlappingStatementAncestor(s, lines))
                .toList();
    }

    private static boolean overlapsLines(Statement statement, Set<Integer> lines) {
        return statement.getRange().map(range -> {
            for (int line = range.begin.line; line <= range.end.line; line++) {
                if (lines.contains(line)) {
                    return true;
                }
            }
            return false;
        }).orElse(false);
    }

    // Keep only the outermost overlapping statements, so a control statement and its nested body are
    // not both counted (which would double-count tokens).
    private static boolean hasOverlappingStatementAncestor(Statement statement, Set<Integer> lines) {
        Node parent = statement.getParentNode().orElse(null);
        while (parent != null) {
            if (parent instanceof Statement ancestor && !(parent instanceof BlockStmt)
                    && overlapsLines(ancestor, lines)) {
                return true;
            }
            parent = parent.getParentNode().orElse(null);
        }
        return false;
    }

    private static boolean isControlStatement(Statement statement) {
        return statement.isIfStmt()
                || statement.isForStmt()
                || statement.isForEachStmt()
                || statement.isWhileStmt()
                || statement.isDoStmt()
                || statement.isSwitchStmt()
                || statement.isTryStmt();
    }

    private static List<String> tokens(Node node, TokenView view) {
        Optional<com.github.javaparser.TokenRange> range = node.getTokenRange();
        if (range.isEmpty()) {
            return List.of();
        }
        List<String> tokens = new ArrayList<>();
        for (JavaToken token : range.get()) {
            String text = token.getText();
            if (text == null || text.isBlank() || isComment(text)) {
                continue;
            }
            tokens.add(switch (view) {
                case RAW, T1 -> text;
                case T2 -> normalizeT2(text);
            });
        }
        return List.copyOf(tokens);
    }

    /**
     * A statement's OWN tokens: its tokens minus those belonging to any nested non-block statement.
     * For a leaf statement this is its full text; for a control statement (for/if/while) it is just
     * the header (condition/init/update), since the body statements are counted as their own units.
     * This keeps the statement-level comparison from double-representing a loop and its body.
     */
    private static List<String> ownTokens(Statement statement, TokenView view) {
        Optional<com.github.javaparser.TokenRange> range = statement.getTokenRange();
        if (range.isEmpty()) {
            return List.of();
        }
        List<com.github.javaparser.Range> nestedRanges = statement.findAll(Statement.class).stream()
                .filter(s -> s != statement && !(s instanceof BlockStmt))
                .flatMap(s -> s.getRange().stream())
                .toList();
        List<String> tokens = new ArrayList<>();
        for (JavaToken token : range.get()) {
            String text = token.getText();
            if (text == null || text.isBlank() || isComment(text)) {
                continue;
            }
            Optional<com.github.javaparser.Range> tokenRange = token.getRange();
            if (tokenRange.isPresent()
                    && nestedRanges.stream().anyMatch(nested -> nested.contains(tokenRange.get()))) {
                continue; // this token belongs to a nested statement, counted on its own
            }
            tokens.add(switch (view) {
                case RAW, T1 -> text;
                case T2 -> normalizeT2(text);
            });
        }
        return List.copyOf(tokens);
    }

    private static boolean isComment(String text) {
        String trimmed = text.stripLeading();
        return trimmed.startsWith("//") || trimmed.startsWith("/*");
    }

    private static String normalizeT2(String token) {
        if (JAVA_KEYWORDS.contains(token)) {
            return token;
        }
        if (token.matches("\"([^\"\\\\]|\\\\.)*\"")) {
            return "STR";
        }
        if (token.matches("'([^'\\\\]|\\\\.)*'")) {
            return "CHR";
        }
        if (token.matches("[0-9][0-9_]*(\\.[0-9_]+)?([eE][+-]?[0-9_]+)?[fFdDlL]?")) {
            return "NUM";
        }
        if (token.matches("[A-Za-z_$][A-Za-z0-9_$]*")) {
            return "ID";
        }
        return token;
    }

    static RenameEvidence renameEvidence(CodeRegion left, CodeRegion right) {
        int limit = Math.min(left.rawTokens().size(), right.rawTokens().size());
        Map<String, String> renameMap = new HashMap<>();
        boolean conflict = false;
        for (int i = 0; i < limit; i++) {
            String leftRaw = left.rawTokens().get(i);
            String rightRaw = right.rawTokens().get(i);
            if (leftRaw.equals(rightRaw)) {
                continue;
            }
            if (i >= left.t2NormalizedTokens().size() || i >= right.t2NormalizedTokens().size()) {
                continue;
            }
            if (!"ID".equals(left.t2NormalizedTokens().get(i))
                    || !"ID".equals(right.t2NormalizedTokens().get(i))) {
                continue;
            }
            String previous = renameMap.putIfAbsent(leftRaw, rightRaw);
            if (previous != null && !previous.equals(rightRaw)) {
                conflict = true;
            }
        }
        return new RenameEvidence(Map.copyOf(renameMap), conflict);
    }

    static boolean literalChanged(CodeRegion left, CodeRegion right) {
        return changedByNormalizedClass(left, right, Set.of("STR", "CHR", "NUM"));
    }

    static boolean identifierChanged(CodeRegion left, CodeRegion right) {
        return changedByNormalizedClass(left, right, Set.of("ID"));
    }

    private static boolean changedByNormalizedClass(CodeRegion left, CodeRegion right, Set<String> classes) {
        int limit = Math.min(left.rawTokens().size(), right.rawTokens().size());
        for (int i = 0; i < limit; i++) {
            if (left.rawTokens().get(i).equals(right.rawTokens().get(i))) {
                continue;
            }
            if (i < left.t2NormalizedTokens().size()
                    && i < right.t2NormalizedTokens().size()
                    && classes.contains(left.t2NormalizedTokens().get(i))
                    && left.t2NormalizedTokens().get(i).equals(right.t2NormalizedTokens().get(i))) {
                return true;
            }
        }
        return false;
    }

    private static String signatureOf(MethodDeclaration md) {
        String params = md.getParameters().stream()
                .map(p -> p.getType().asString())
                .collect(Collectors.joining(","));
        return md.getNameAsString() + "(" + params + ")";
    }

    static double lcsSimilarity(List<String> left, List<String> right) {
        int max = Math.max(left.size(), right.size());
        if (max == 0) {
            return 1.0;
        }
        return (double) lcsLength(left, right) / max;
    }

    static double jaccardSimilarity(List<String> left, List<String> right) {
        Set<String> a = new LinkedHashSet<>(left);
        Set<String> b = new LinkedHashSet<>(right);
        if (a.isEmpty() && b.isEmpty()) {
            return 1.0;
        }
        Set<String> intersection = new LinkedHashSet<>(a);
        intersection.retainAll(b);
        Set<String> union = new LinkedHashSet<>(a);
        union.addAll(b);
        return union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
    }

    static int lcsLength(List<String> left, List<String> right) {
        int[][] dp = new int[left.size() + 1][right.size() + 1];
        for (int i = left.size() - 1; i >= 0; i--) {
            for (int j = right.size() - 1; j >= 0; j--) {
                dp[i][j] = left.get(i).equals(right.get(j))
                        ? 1 + dp[i + 1][j + 1]
                        : Math.max(dp[i + 1][j], dp[i][j + 1]);
            }
        }
        return dp[0][0];
    }

    private enum TokenView {
        RAW,
        T1,
        T2
    }
}
