package com.ziqi.codesim.preprocess;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class Tokenizer {
    public static List<String> tokenizeForBaseline(String code, Lang.Spec spec) {
        return tokenize(code, spec);
    }

    private static List<String> tokenize(String code, Lang.Spec spec) {
        List<String> tokens = new ArrayList<>();
        int n = code.length();
        int i = 0;
        while (i < n) {
            char c = code.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }
            if (c == '"') {
                i++;
                while (i < n) {
                    char ch = code.charAt(i);
                    if (ch == '\\') {
                        i += Math.min(2, n - i);
                    } else if (ch == '"') {
                        i++;
                        break;
                    } else {
                        i++;
                    }
                }
                tokens.add("STR");
                continue;
            }
            if (c == '/' && i + 1 < n) {
                char next = code.charAt(i + 1);
                if (next == '/') {
                    i += 2;
                    while (i < n && code.charAt(i) != '\n') i++;
                    continue;
                } else if (next == '*') {
                    i += 2;
                    while (i + 1 < n && !(code.charAt(i) == '*' && code.charAt(i + 1) == '/')) i++;
                    i = (i + 1 < n) ? i + 2 : n;
                    continue;
                }
            }
            if (c == '\'') {
                i++;
                while (i < n) {
                    char ch = code.charAt(i);
                    if (ch == '\\') {
                        i += Math.min(2, n - i);
                    } else if (ch == '\'') {
                        i++;
                        break;
                    } else {
                        i++;
                    }
                }
                tokens.add("CHAR");
                continue;
            }
            if (Character.isLetter(c) || c == '_') {
                int start = i;
                i++;
                while (i < n) {
                    char ch = code.charAt(i);
                    if (Character.isLetterOrDigit(ch) || ch == '_') {
                        i++;
                    } else {
                        break;
                    }
                }
                String word = code.substring(start, i);
                tokens.add(spec.keywords.contains(word) ? word : "ID");
                continue;
            }
            if (c == '0' && i + 1 < n && (code.charAt(i + 1) == 'x' || code.charAt(i + 1) == 'X')) {
                i += 2;
                while (i < n && isHexDigit(code.charAt(i))) i++;
                tokens.add("NUM");
                continue;
            }
            if (Character.isDigit(c) || (c == '.' && i + 1 < n && Character.isDigit(code.charAt(i + 1)))) {
                i++;
                boolean hasDot = (c == '.');
                boolean hasExp = false;
                while (i < n) {
                    char ch = code.charAt(i);
                    if (Character.isDigit(ch)) {
                        i++;
                    } else if (!hasDot && !hasExp && ch == '.') {
                        i++;
                        hasDot = true;
                    } else if (!hasExp && (ch == 'e' || ch == 'E')) {
                        int ePos = i;
                        i++;
                        hasExp = true;
                        if (i < n && (code.charAt(i) == '+' || code.charAt(i) == '-')) i++;
                        int expDigitsStart = i;
                        while (i < n && Character.isDigit(code.charAt(i))) i++;
                        if (expDigitsStart == i) i = ePos;
                        break;
                    } else {
                        break;
                    }
                }
                tokens.add("NUM");
                continue;
            }
            if (i + 1 < n && isTwoCharOperator(code.substring(i, i + 2))) {
                tokens.add(code.substring(i, i + 2));
                i += 2;
                continue;
            }
            tokens.add(String.valueOf(c));
            i++;
        }
        return tokens;
    }

    private static boolean isHexDigit(char ch) {
        return Character.isDigit(ch) || (ch >= 'a' && ch <= 'f') || (ch >= 'A' && ch <= 'F');
    }

    private static boolean isTwoCharOperator(String s) {
        return Set.of(
            "==", "!=", "<=", ">=", "&&", "||", "++", "--", "+=",
            "-=", "*=", "/=", "%=", "<<", ">>", "&=", "|=", "^=", "->"
        ).contains(s);
    }
}
