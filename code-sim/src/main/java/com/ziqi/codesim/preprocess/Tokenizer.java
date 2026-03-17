// Ziqi Liu Meng Project-Based Software Engineering
// This file is used for geting tokens from code
package com.ziqi.codesim.preprocess;

import java.util.List;
import java.util.ArrayList;
import java.util.Set;

public class Tokenizer {
    public static List<String> tokenizeForBaseline(String code, Lang.Spec spec) {
        return tokenize(code, spec);
    }

    private static List<String> tokenize(String code, Lang.Spec spec) {
        List<String> tokens = new ArrayList<>();
        int n = code.length();
        // List<String> stringStored = new ArrayList<>();
        int i = 0;
        while (i < n) {
            char c = code.charAt(i);
            // Skip whitespace
            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }
            // Store Sring literals
            if (c == '"') {
                i++;
                while (i < n) {
                    char ch = code.charAt(i);
                    if (ch == '\\') { 
                        i += Math.min(2, n - i); // Skip the escaped character
                        continue;
                    } else if (ch == '"') { // End of string literal
                        i++;
                        break;
                    } else {
                        i++;
                    }
                }
                tokens.add("STR"); // “Hello”
                // stringStored.add(code.substring(start, i));
                continue;
            }
            // Skip comments
            if (c == '/' && i + 1 < n) {
                char next = code.charAt(i + 1);
                if (next == '/') {
                    // Skip until end of line
                    i += 2;
                    while (i < n && code.charAt(i) != '\n') i++;
                    continue;
                } else if (next == '*') {
                    // Skip until "*/"
                    i += 2;
                    while (i + 1 < n && !(code.charAt(i) == '*' && code.charAt(i + 1) == '/')) i++;
                    if (i + 1 < n) {
                        i += 2; // Skip "*/"
                    } else {
                        i = n;
                    }
                    continue;
                }
            }
            // Store one character tokens ('a', '\n')
            if (c == '\'') {
                i++;
                while (i < n) {
                    char ch = code.charAt(i);
                    if (ch == '\\') { 
                        i += Math.min(2, n - i); // Skip the escaped character
                        continue;
                    } else if (ch == '\'') { // End of char literal
                        i++;
                        break;
                    } else {
                        i++;
                    }
                }
                tokens.add("CHAR");
                // stringStored.add(code.substring(start, i));
                continue;
            }
            // Store keywords and identifiers ([A-Za-z_])
            if (Character.isLetter(c) || c == '_') {
                // Get the whole word
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
            //Store numbers
            // Hex
            if (c == '0' && i + 1 < n && (code.charAt(i + 1) == 'x' || code.charAt(i + 1) == 'X')) {
                i += 2; 
                while (i < n && (Character.isDigit(code.charAt(i)) || (code.charAt(i) >= 'a' && code.charAt(i) <= 'f') || (code.charAt(i) >= 'A' && code.charAt(i) <= 'F'))) {
                    i++;
                }
                tokens.add("NUM");
                continue;
            }
            // Decimal, float, scientific notation (including .5)
            if (Character.isDigit(c) || (c == '.' && i + 1 < n && Character.isDigit(code.charAt(i + 1)))) {
                i++;
                boolean hasDot = (c == '.');
                boolean hasExp = false;
                while (i < n) {
                    char ch = code.charAt(i);
                    if (Character.isDigit(ch)) {
                        i++; // 12
                        continue;
                    } else if (!hasDot && !hasExp && ch == '.') {
                        i++; // 12.
                        hasDot = true;
                        continue;
                    } else if (!hasExp && (ch == 'e' || ch == 'E')) {
                        int ePos = i; // Remember "e" position
                        i++; // 12e, 12.345E, but no "." after "e"
                        hasExp = true;
                        if (i < n) {
                            char sign = code.charAt(i); // 12e+5, 12e-3
                            if (sign == '+' || sign == '-') i++;
                        }
                        int expDigitsStart = i; // Remember the start of exponent digits
                        while (i < n && Character.isDigit(code.charAt(i))) i++;
                        if (expDigitsStart == i) { // No digits after "e", rollback
                            i = ePos;
                        }
                        break;
                    }
                    break; 
                }
                tokens.add("NUM");
                continue;
            }
            // Operators and delimiters
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
    
    private static boolean isTwoCharOperator(String s) {
        return Set.of(
            "==", "!=", "<=", ">=", "&&", "||", "++", "--", "+=", 
            "-=", "*=", "/=", "%=", "<<", ">>", "&=", "|=", "^=", "->"
        ).contains(s);
    }
}
