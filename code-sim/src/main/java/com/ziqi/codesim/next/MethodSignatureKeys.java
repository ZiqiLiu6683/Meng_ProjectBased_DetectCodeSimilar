package com.ziqi.codesim.next;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Shared, overload-aware method-signature normalization.
 *
 * <p>WALA bytecode signatures (e.g. {@code "Foo.bar(ILjava/lang/String;)V"}) and source-side
 * region display names (e.g. {@code "Foo.bar(int,String)"}) describe the same method in different
 * notations. These helpers erase both to a comparable lowercase key ({@code "bar(int,string)"}) so
 * overloads can be matched precisely instead of being collapsed by name alone.
 */
final class MethodSignatureKeys {

    private MethodSignatureKeys() {
    }

    /** Method name only: strips the parameter list and any declaring-type / package qualifier. */
    static String simpleName(String value) {
        String cleaned = value == null ? "" : value;
        int paren = cleaned.indexOf('(');
        if (paren >= 0) {
            cleaned = cleaned.substring(0, paren);
        }
        int dot = cleaned.lastIndexOf('.');
        if (dot >= 0) {
            cleaned = cleaned.substring(dot + 1);
        }
        return cleaned;
    }

    static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }

    static String signatureKey(String name, List<String> erasedTypes) {
        return normalize(name) + "(" + String.join(",", erasedTypes) + ")";
    }

    /**
     * Source parameter types from a display name like {@code "Foo.bar(int, java.util.List<String>[])"}:
     * split at top level (commas inside generics are ignored) and erase each to a lowercase simple
     * type so it lines up with the bytecode-descriptor view ({@code "list"}, {@code "string"},
     * {@code "int[]"}, ...).
     */
    static List<String> sourceParamTypes(String displayName) {
        String params = between(displayName, '(', ')');
        List<String> types = new ArrayList<>();
        if (params == null || params.isBlank()) {
            return types;
        }
        int depth = 0;
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < params.length(); i++) {
            char c = params.charAt(i);
            if (c == '<') {
                depth++;
            } else if (c == '>') {
                depth--;
            } else if (c == ',' && depth == 0) {
                types.add(eraseSourceType(current.toString()));
                current.setLength(0);
                continue;
            }
            current.append(c);
        }
        if (current.length() > 0) {
            types.add(eraseSourceType(current.toString()));
        }
        return types;
    }

    private static String eraseSourceType(String raw) {
        String type = raw.trim();
        int dims = 0;
        while (type.endsWith("...")) {
            dims++;
            type = type.substring(0, type.length() - 3).trim();
        }
        while (type.endsWith("[]")) {
            dims++;
            type = type.substring(0, type.length() - 2).trim();
        }
        int generic = type.indexOf('<');
        if (generic >= 0) {
            type = type.substring(0, generic);
        }
        int dot = type.lastIndexOf('.');
        if (dot >= 0) {
            type = type.substring(dot + 1);
        }
        return normalize(type) + "[]".repeat(dims);
    }

    /**
     * JVM descriptor parameter list from a WALA signature like {@code "Foo.bar(ILjava/lang/String;[I)V"}:
     * walk the descriptor between the parentheses and erase each to a lowercase simple type.
     */
    static List<String> bytecodeParamTypes(String rawMethodKey) {
        String descriptor = between(rawMethodKey, '(', ')');
        List<String> types = new ArrayList<>();
        if (descriptor == null || descriptor.isBlank()) {
            return types;
        }
        int i = 0;
        while (i < descriptor.length()) {
            int dims = 0;
            while (i < descriptor.length() && descriptor.charAt(i) == '[') {
                dims++;
                i++;
            }
            if (i >= descriptor.length()) {
                break;
            }
            char c = descriptor.charAt(i);
            String base;
            if (c == 'L') {
                int end = descriptor.indexOf(';', i);
                if (end < 0) {
                    break;
                }
                String fqn = descriptor.substring(i + 1, end).replace('/', '.');
                int dot = fqn.lastIndexOf('.');
                base = dot >= 0 ? fqn.substring(dot + 1) : fqn;
                i = end + 1;
            } else {
                base = primitiveDescriptor(c);
                i++;
            }
            types.add(normalize(base) + "[]".repeat(dims));
        }
        return types;
    }

    private static String primitiveDescriptor(char c) {
        return switch (c) {
            case 'B' -> "byte";
            case 'C' -> "char";
            case 'D' -> "double";
            case 'F' -> "float";
            case 'I' -> "int";
            case 'J' -> "long";
            case 'S' -> "short";
            case 'Z' -> "boolean";
            case 'V' -> "void";
            default -> String.valueOf(c);
        };
    }

    private static String between(String value, char open, char close) {
        if (value == null) {
            return null;
        }
        int start = value.indexOf(open);
        int end = value.lastIndexOf(close);
        if (start < 0 || end < 0 || end <= start) {
            return null;
        }
        return value.substring(start + 1, end);
    }
}
