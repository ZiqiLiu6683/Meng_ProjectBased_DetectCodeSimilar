package com.ziqi.codesim.preprocess;

import java.util.List;
import java.util.ArrayList;

public class Tokenizer {
    public static List<String> tokenizeForBaseline(String code) {
        String cleaned = removeComments(code);
        return tokenize(cleaned);
    }

    private static String removeComments(String code) {
        // Remove "// AAAAA"
        code = code.replaceAll("//.*", "");
        // Remove "/* AAAAA */"
        code = code.replaceAll("/\\*.*?\\*/", "");
        return code;
    } 

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
