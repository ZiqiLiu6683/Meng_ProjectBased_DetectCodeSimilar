// Ziqi Liu Meng Project-Based Software Engineering
// This file is used for geting tokens from code
package com.ziqi.codesim.preprocess;

import java.util.List;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class Tokenizer {
    public static List<String> tokenizeForBaseline(String code) {
        // String cleaned = removeComments(code);
        // return tokenize(cleaned);
        return tokenize(code);
    }
    // Keywords
    private static final Set<String> KEYWORDS = new HashSet<>(List.of(
        "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class",
        "const", "continue", "default", "do", "double", "else", "enum", "extends",
        "final", "finally", "float", "for", "goto", "if", "implements", "import",
        "instanceof", "int", "interface", "long", "native", "new", "package",
        "private", "protected", "public", "return", "short", "static",
        "strictfp", "super", "switch", "synchronized", "this",
        "throw", "throws", "transient", "try", "void", "volatile", "while"
    ));

    // Ver1
    // private static String removeComments(String code) {
    //     // Remove "// AAAAA"
    //     code = code.replaceAll("//.*", "");
    //     // Remove "/* AAAAA */"
    //     code = code.replaceAll("/\\*.*?\\*/", "");
    //     return code;
    // } 

    private static List<String> tokenize(String code) {
        List<String> tokens = new ArrayList<>();
        int n = code.length();
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < n; i++) {
            char c = code.charAt(i);
            // Collect letters, digits, and underscores as a single token
            if (Character.isLetterOrDigit(c) || c == '_') {
                current.append(c);
                continue;
            } 
            if (current.length() > 0) {
                tokens.add(current.toString());
                current.setLength(0);
            }
            if (Character.isWhitespace(c)) continue;
            // Collect single character tokens (=, +, -, etc. "=" in x=1)
            tokens.add(String.valueOf(c));
    }
        // Add the last token if exists
        if (current.length() > 0) {
            tokens.add(current.toString());
        }
        return tokens;
    }     
}
