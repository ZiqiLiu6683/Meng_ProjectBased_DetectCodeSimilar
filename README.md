# Meng_ProjectBased_DetectCodeSimilar
Meng Project based

# Winnowing Functions and Rules
## Normalization / Preprocess
- Remove comments: "//AAAAAA" and /*AAAAAA*/.
- Whitespace is token separator.
## Tokenization
Two modes:
- SIMPLE: keep identifiers/numbers/strings as-is. (Completed)
- NORMALIZED:
  - identifiers -> ID
  - numbers -> NUM
  - string literals -> STR
  - char literals -> CHAR
  - keywords kept (if, for, while, return)
  - operators kept, including multi-char ops (==, <=, ->, &&)
## k-gram / hash (Completed)
- k-gram is contiguous sequence of k tokens.
- rolling hash uses 64-bit long.
## Winnowing （Completed）
- window size w.
- for each window choose minimum hash; ties choose RIGHTMOST.
## Similarity
- Jaccard(A,B) = intersection / union (Completed)
- Containment(A in B) = intersection / |A|
- Containment(B in A) = intersection / |B|
