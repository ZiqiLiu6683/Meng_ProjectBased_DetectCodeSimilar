package com.ziqi.codesim.next.semantic.compilation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Conservative javac-diagnostic-driven dependency stub generator.
 *
 * <p>Stubs exist only to make the user file analyzable; they are compiled into a separate context
 * directory and are never WALA Application classes or clone candidates. The generator deliberately
 * handles only missing external types and their directly-observed members. It never edits the input
 * source and never invents executable semantics for Type-4 proof.
 */
final class StubGenerator {
    static final String VERSION = "3";

    private static final Pattern PACKAGE = Pattern.compile(
            "(?m)^\\s*package\\s+([A-Za-z_$][A-Za-z0-9_$.]*)\\s*;");
    private static final Pattern IMPORT = Pattern.compile(
            "(?m)^\\s*import\\s+(static\\s+)?([A-Za-z_$][A-Za-z0-9_$.]*)(\\.\\*)?\\s*;");
    private static final Pattern DECLARED_TYPE = Pattern.compile(
            "\\b(?:class|interface|enum|record|@interface)\\s+([A-Za-z_$][A-Za-z0-9_$]*)");
    private static final Pattern SYMBOL_CLASS = Pattern.compile(
            "(?m)symbol:\\s+(?:class|interface|enum|record)\\s+([A-Za-z_$][A-Za-z0-9_$]*)");
    private static final Pattern SYMBOL_METHOD = Pattern.compile(
            "(?m)symbol:\\s+method\\s+([A-Za-z_$][A-Za-z0-9_$]*)\\s*\\(");
    private static final Pattern SYMBOL_VARIABLE = Pattern.compile(
            "(?m)symbol:\\s+variable\\s+([A-Za-z_$][A-Za-z0-9_$]*)");
    private static final Pattern LOCATION_TYPE = Pattern.compile(
            "(?m)location:\\s+(?:variable\\s+\\w+\\s+of\\s+type|class|interface)\\s+([A-Za-z_$][A-Za-z0-9_$.]*)");
    private static final Pattern VARIABLE_DECL = Pattern.compile(
            "\\b([A-Z_$][A-Za-z0-9_$.]*(?:\\s*<[^;=(){}]+>)?(?:\\s*\\[\\s*])*)\\s+([a-zA-Z_$][A-Za-z0-9_$]*)\\b");
    private static final Pattern MEMBER_CALL = Pattern.compile(
            "\\b([A-Za-z_$][A-Za-z0-9_$]*)\\s*\\.\\s*([A-Za-z_$][A-Za-z0-9_$]*)\\s*\\(");
    private static final Pattern FIELD_ACCESS = Pattern.compile(
            "\\b([A-Za-z_$][A-Za-z0-9_$]*)\\s*\\.\\s*([A-Za-z_$][A-Za-z0-9_$]*)\\b(?!\\s*\\()");
    private static final Pattern EXTENDS_TYPE = Pattern.compile(
            "\\bextends\\s+([A-Za-z_$][A-Za-z0-9_$.]*(?:\\s*<[^>{}]+>)?)");
    private static final Pattern IMPLEMENTS_TYPES = Pattern.compile(
            "\\bimplements\\s+([^\\{]+)");

    private final Map<String, StubType> types = new LinkedHashMap<>();

    int update(String source, List<CompilerDiagnostic> diagnostics) {
        SourceNames names = SourceNames.parse(source);
        int before = fingerprintSize();
        for (CompilerDiagnostic diagnostic : diagnostics) {
            if (diagnostic.kind() != javax.tools.Diagnostic.Kind.ERROR) {
                continue;
            }
            String sourceLine = line(source, diagnostic.line());
            Matcher importLine = IMPORT.matcher(sourceLine);
            while (importLine.find()) {
                String importedType = importLine.group(2);
                if (importLine.group(3) != null) {
                    if (importLine.group(1) != null) {
                        addType(importedType, names, source);
                    } else if (!importedType.startsWith("java.")) {
                        addPackageMarker(importedType);
                    }
                } else if (importLine.group(1) != null) {
                    String member = simpleName(importedType);
                    int memberSeparator = importedType.lastIndexOf('.');
                    importedType = memberSeparator < 0
                            ? importedType : importedType.substring(0, memberSeparator);
                    addType(importedType, names, source);
                    StubType owner = findType(importedType);
                    if (owner != null) {
                        // javac will disambiguate whether the imported member is used as a field
                        // or a call. Declaring both is legal and avoids inventing a return type.
                        owner.fields.add(member);
                        owner.methods.add(member);
                    }
                }
                if (importLine.group(3) == null && !importedType.startsWith("java.")) {
                    addType(importedType, names, source);
                }
            }
            Matcher symbol = SYMBOL_CLASS.matcher(diagnostic.message());
            while (symbol.find()) {
                addType(names.resolve(symbol.group(1)), names, source);
            }
        }

        // Resolve members after all missing types have been collected. javac may report a missing
        // inherited member before or after the diagnostic for its absent superclass.
        for (CompilerDiagnostic diagnostic : diagnostics) {
            if (diagnostic.kind() != javax.tools.Diagnostic.Kind.ERROR) {
                continue;
            }
            Matcher method = SYMBOL_METHOD.matcher(diagnostic.message());
            Matcher location = LOCATION_TYPE.matcher(diagnostic.message());
            if (method.find() && location.find()) {
                for (StubType owner : ownersForLocation(location.group(1), names)) {
                    owner.methods.add(method.group(1));
                }
            }
            Matcher variable = SYMBOL_VARIABLE.matcher(diagnostic.message());
            location = LOCATION_TYPE.matcher(diagnostic.message());
            if (variable.find() && location.find()) {
                for (StubType owner : ownersForLocation(location.group(1), names)) {
                    owner.fields.add(variable.group(1));
                }
            }
        }

        // Once a type is known missing, collect only members visibly used on that type. This avoids
        // repeatedly invoking javac just to learn one member at a time.
        Map<String, String> variableTypes = variableTypes(source, names);
        Matcher calls = MEMBER_CALL.matcher(source);
        while (calls.find()) {
            String receiver = calls.group(1);
            String ownerName = variableTypes.getOrDefault(receiver, names.resolve(receiver));
            StubType owner = findType(ownerName);
            if (owner != null) {
                owner.methods.add(calls.group(2));
            }
        }
        Matcher fields = FIELD_ACCESS.matcher(source);
        while (fields.find()) {
            String receiver = fields.group(1);
            String ownerName = variableTypes.getOrDefault(receiver, names.resolve(receiver));
            StubType owner = findType(ownerName);
            if (owner != null && !"class".equals(fields.group(2))) {
                owner.fields.add(fields.group(2));
            }
        }
        return fingerprintSize() - before;
    }

    int writeSources(Path root) throws IOException {
        int count = 0;
        for (StubType type : types.values()) {
            Path directory = type.packageName.isBlank()
                    ? root : root.resolve(type.packageName.replace('.', '/'));
            Files.createDirectories(directory);
            Files.writeString(directory.resolve(type.simpleName + ".java"), type.render(),
                    StandardCharsets.UTF_8);
            count++;
        }
        return count;
    }

    List<Path> sourceFiles(Path root) throws IOException {
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        try (var stream = Files.walk(root)) {
            return stream.filter(path -> path.toString().endsWith(".java"))
                    .sorted()
                    .toList();
        }
    }

    private void addType(String qualifiedName, SourceNames names, String source) {
        if (qualifiedName == null || qualifiedName.isBlank() || qualifiedName.startsWith("java.")) {
            return;
        }
        String cleaned = erase(qualifiedName);
        String simple = simpleName(cleaned);
        if (names.declaredTypes.contains(simple) || isBuiltIn(simple)) {
            return;
        }
        String resolved = cleaned.contains(".") ? cleaned : names.resolve(cleaned);
        StubKind kind = kindOf(simple, source);
        types.computeIfAbsent(resolved, ignored -> StubType.of(resolved, kind));
    }

    private void addPackageMarker(String packageName) {
        String qualifiedName = packageName + ".__CodeSimPackageMarker";
        types.computeIfAbsent(qualifiedName,
                ignored -> StubType.of(qualifiedName, StubKind.CLASS));
    }

    private List<StubType> ownersForLocation(String locationName, SourceNames names) {
        StubType exact = findType(names.resolve(locationName));
        if (exact != null) {
            return List.of(exact);
        }
        if (!names.declaredTypes.contains(simpleName(locationName))) {
            return List.of();
        }
        return names.parentTypes.stream()
                .map(this::findType)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
    }

    private StubType findType(String name) {
        if (name == null) {
            return null;
        }
        String erased = erase(name);
        StubType exact = types.get(erased);
        if (exact != null) {
            return exact;
        }
        String simple = simpleName(erased);
        return types.values().stream()
                .filter(type -> type.simpleName.equals(simple))
                .findFirst().orElse(null);
    }

    private int fingerprintSize() {
        int size = types.size();
        for (StubType type : types.values()) {
            size += type.methods.size() + type.fields.size();
        }
        return size;
    }

    private static Map<String, String> variableTypes(String source, SourceNames names) {
        Map<String, String> variables = new HashMap<>();
        Matcher matcher = VARIABLE_DECL.matcher(source);
        while (matcher.find()) {
            variables.put(matcher.group(2), names.resolve(erase(matcher.group(1))));
        }
        return variables;
    }

    private static StubKind kindOf(String simpleName, String source) {
        if (Pattern.compile("@\\s*" + Pattern.quote(simpleName) + "\\b").matcher(source).find()) {
            return StubKind.ANNOTATION;
        }
        if (Pattern.compile("\\bimplements\\s+[^\\{;]*\\b" + Pattern.quote(simpleName) + "\\b")
                .matcher(source).find()) {
            return StubKind.INTERFACE;
        }
        if (Pattern.compile("\\b(?:throws|catch\\s*\\([^)]*)[^\\{;]*\\b"
                + Pattern.quote(simpleName) + "\\b").matcher(source).find()) {
            return StubKind.EXCEPTION;
        }
        return StubKind.CLASS;
    }

    private static String line(String source, long lineNumber) {
        if (lineNumber <= 0) {
            return "";
        }
        String[] lines = source.split("\\R", -1);
        return lineNumber <= lines.length ? lines[(int) lineNumber - 1] : "";
    }

    private static String erase(String type) {
        return type.replaceAll("<.*>", "")
                .replace("[]", "")
                .replaceAll("\\s+", "")
                .strip();
    }

    private static String simpleName(String name) {
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1) : name;
    }

    private static boolean isBuiltIn(String name) {
        return Set.of("String", "Object", "Class", "Integer", "Long", "Short", "Byte",
                "Character", "Boolean", "Double", "Float", "Math", "System", "Exception",
                "RuntimeException", "Throwable", "Override", "Deprecated", "SuppressWarnings")
                .contains(name);
    }

    private enum StubKind { CLASS, INTERFACE, ANNOTATION, EXCEPTION }

    private static final class StubType {
        private final String packageName;
        private final String simpleName;
        private final StubKind kind;
        private final Set<String> methods = new LinkedHashSet<>();
        private final Set<String> fields = new LinkedHashSet<>();

        private StubType(String packageName, String simpleName, StubKind kind) {
            this.packageName = packageName;
            this.simpleName = simpleName;
            this.kind = kind;
        }

        static StubType of(String qualifiedName, StubKind kind) {
            int dot = qualifiedName.lastIndexOf('.');
            return new StubType(dot < 0 ? "" : qualifiedName.substring(0, dot),
                    dot < 0 ? qualifiedName : qualifiedName.substring(dot + 1), kind);
        }

        String render() {
            StringBuilder out = new StringBuilder();
            if (!packageName.isBlank()) {
                out.append("package ").append(packageName).append(";\n\n");
            }
            if (kind == StubKind.ANNOTATION) {
                return out.append("public @interface ").append(simpleName).append(" {}\n").toString();
            }
            if (kind == StubKind.INTERFACE) {
                out.append("public interface ").append(simpleName).append(" {\n");
                for (String method : methods) {
                    out.append("  default <T> T ").append(method)
                            .append("(Object... args) { return null; }\n");
                }
                return out.append("}\n").toString();
            }
            out.append("public class ").append(simpleName);
            if (kind == StubKind.EXCEPTION) {
                out.append(" extends Exception");
            }
            out.append(" {\n");
            out.append("  public ").append(simpleName).append("(Object... args) {}\n");
            for (String field : fields) {
                // A static field is legal through either Type.FIELD or an instance expression.
                // The inverse is not true and caused non-static-reference failures in BCB files.
                out.append("  public static Object ").append(field).append(";\n");
            }
            for (String method : methods) {
                out.append("  public static <T> T ").append(method)
                        .append("(Object... args) { return null; }\n");
            }
            return out.append("}\n").toString();
        }
    }

    private record SourceNames(String packageName, Map<String, String> explicitImports,
                               List<String> wildcardImports, Set<String> declaredTypes,
                               List<String> parentTypes) {
        static SourceNames parse(String source) {
            Matcher packageMatcher = PACKAGE.matcher(source);
            String packageName = packageMatcher.find() ? packageMatcher.group(1) : "";
            Map<String, String> explicit = new HashMap<>();
            List<String> wildcard = new ArrayList<>();
            Matcher imports = IMPORT.matcher(source);
            while (imports.find()) {
                if (imports.group(1) != null) {
                    // Static imports expose members, not a simple type name in source.
                    continue;
                }
                String value = imports.group(2);
                if (imports.group(3) != null) {
                    wildcard.add(value);
                } else {
                    explicit.put(simpleName(value), value);
                }
            }
            Set<String> declared = new HashSet<>();
            Matcher declarations = DECLARED_TYPE.matcher(source);
            while (declarations.find()) {
                declared.add(declarations.group(1));
            }
            SourceNames preliminary = new SourceNames(
                    packageName, Map.copyOf(explicit), List.copyOf(wildcard),
                    Set.copyOf(declared), List.of());
            List<String> parents = new ArrayList<>();
            Matcher extended = EXTENDS_TYPE.matcher(source);
            while (extended.find()) {
                parents.add(preliminary.resolve(extended.group(1)));
            }
            Matcher implemented = IMPLEMENTS_TYPES.matcher(source);
            while (implemented.find()) {
                for (String value : implemented.group(1).split(",")) {
                    String cleaned = erase(value);
                    if (!cleaned.isBlank()) {
                        parents.add(preliminary.resolve(cleaned));
                    }
                }
            }
            return new SourceNames(
                    packageName, Map.copyOf(explicit), List.copyOf(wildcard),
                    Set.copyOf(declared), List.copyOf(parents));
        }

        String resolve(String name) {
            String erased = erase(name);
            if (erased.contains(".")) {
                return erased;
            }
            String imported = explicitImports.get(erased);
            if (imported != null) {
                return imported;
            }
            if (wildcardImports.size() == 1 && !wildcardImports.get(0).startsWith("java.")) {
                return wildcardImports.get(0) + "." + erased;
            }
            return packageName.isBlank() ? erased : packageName + "." + erased;
        }
    }
}
