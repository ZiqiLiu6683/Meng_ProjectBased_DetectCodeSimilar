// Ziqi Liu Meng Project-Based Software Engineering
// This file is used for defining the programming language (Java, C for now) 
// and providing related keywords
package com.ziqi.codesim.preprocess;

import java.util.Set;

public class Lang {
    public enum Language {JAVA, C}
    public static final class Spec {
        public final Language language;
        public final Set<String> keywords;
        private Spec(Language language, Set<String> keywords) {
            this.language = language;
            this.keywords = keywords;
        }
    }
    public static Spec detectLanguage(String filePath) {
        if (filePath.endsWith(".java")) return new Spec(Language.JAVA, JAVA_KEYWORDS);
        if (filePath.endsWith(".c") || filePath.endsWith(".h")) return new Spec(Language.C, C_KEYWORDS);
        return new Spec(Language.C, C_KEYWORDS); // C as default
    }
    private static final Set<String> JAVA_KEYWORDS = Set.of(
        "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class",
        "const", "continue", "default", "do", "double", "else", "enum", "extends",
        "final", "finally", "float", "for", "goto", "if", "implements", "import",
        "instanceof", "int", "interface", "long", "native", "new", "package",
        "private", "protected", "public", "return", "short", "static",
        "strictfp", "super", "switch", "synchronized", "this",
        "throw", "throws", "transient", "try", "void",
        "volatile", "while"
    );
    private static final Set<String> C_KEYWORDS = Set.of(
        "auto", "break", "case", "char", "const", "continue", "default",
        "do", "double", "else", "enum", "extern", "float", "for",
        "goto", "if", "inline", "int", "long", "register",
        "restrict", "return", "short", "signed", "sizeof",
        "static", "struct", "switch", "typedef",
        "union", "unsigned", "void", "volatile",
        "_Alignas", "_Alignof", "_Atomic",
        "_Bool", "_Complex", "_Generic",
        "_Imaginary", "_Noreturn",
        "_Static_assert", "_Thread_local"
    );  
}
