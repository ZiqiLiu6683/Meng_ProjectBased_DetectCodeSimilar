
class InvoiceAuditUnrelatedZ {
    String renderReport(String[] rows, int width) {
        StringBuilder builder = new StringBuilder();
        int lineNumber = 1;
        if (rows == null || rows.length == 0) {
            return "";
        }
        for (String row : rows) {
            String cleaned = cleanRow(row);
            if (cleaned.isEmpty()) {
                continue;
            }
            builder.append(padLeft(lineNumber, 4));
            builder.append(" | ");
            builder.append(wrap(cleaned, width));
            builder.append("\n");
            lineNumber++;
        }
        return builder.toString();
    }

    private String cleanRow(String row) {
        if (row == null) {
            return "";
        }
        String trimmed = row.trim();
        StringBuilder builder = new StringBuilder();
        boolean previousSpace = false;
        for (int i = 0; i < trimmed.length(); i++) {
            char ch = trimmed.charAt(i);
            if (Character.isWhitespace(ch)) {
                if (!previousSpace) {
                    builder.append(' ');
                }
                previousSpace = true;
            } else if (ch != '#') {
                builder.append(ch);
                previousSpace = false;
            }
        }
        return builder.toString();
    }

    private String wrap(String text, int width) {
        int safeWidth = Math.max(12, width);
        StringBuilder builder = new StringBuilder();
        int column = 0;
        for (String word : text.split(" ")) {
            if (column + word.length() > safeWidth) {
                builder.append("\n    ");
                column = 4;
            }
            builder.append(word).append(' ');
            column += word.length() + 1;
        }
        return builder.toString().trim();
    }

    private String padLeft(int value, int size) {
        String text = String.valueOf(value);
        StringBuilder builder = new StringBuilder();
        for (int i = text.length(); i < size; i++) {
            builder.append('0');
        }
        builder.append(text);
        return builder.toString();
    }

    int countWords(String[] rows) {
        int count = 0;
        if (rows == null) {
            return count;
        }
        for (String row : rows) {
            String cleaned = cleanRow(row);
            if (!cleaned.isEmpty()) {
                count += cleaned.split(" ").length;
            }
        }
        return count;
    }

    int longestLine(String[] rows) {
        int max = 0;
        if (rows == null) {
            return max;
        }
        for (String row : rows) {
            String cleaned = cleanRow(row);
            if (cleaned.length() > max) {
                max = cleaned.length();
            }
        }
        return max;
    }

    boolean containsPhrase(String[] rows, String phrase) {
        String needle = cleanRow(phrase).toLowerCase();
        if (needle.isEmpty() || rows == null) {
            return false;
        }
        for (String row : rows) {
            if (cleanRow(row).toLowerCase().contains(needle)) {
                return true;
            }
        }
        return false;
    }

    String[] filterRows(String[] rows, int minLength) {
        if (rows == null) {
            return new String[0];
        }
        String[] scratch = new String[rows.length];
        int size = 0;
        for (String row : rows) {
            String cleaned = cleanRow(row);
            if (cleaned.length() >= minLength) {
                scratch[size] = cleaned;
                size++;
            }
        }
        String[] result = new String[size];
        for (int i = 0; i < size; i++) {
            result[i] = scratch[i];
        }
        return result;
    }

    int countLinesWithDigits(String[] rows) {
        int count = 0;
        if (rows == null) {
            return count;
        }
        for (String row : rows) {
            String cleaned = cleanRow(row);
            for (int i = 0; i < cleaned.length(); i++) {
                if (Character.isDigit(cleaned.charAt(i))) {
                    count++;
                    break;
                }
            }
        }
        return count;
    }

    String buildIndex(String[] rows) {
        StringBuilder builder = new StringBuilder();
        String[] filtered = filterRows(rows, 3);
        for (int i = 0; i < filtered.length; i++) {
            builder.append(padLeft(i + 1, 3));
            builder.append(':');
            builder.append(filtered[i].length());
            builder.append(';');
        }
        return builder.toString();
    }

    int estimateWrappedLineCount(String[] rows, int width) {
        int lines = 0;
        if (rows == null) {
            return lines;
        }
        int safeWidth = Math.max(12, width);
        for (String row : rows) {
            String cleaned = cleanRow(row);
            if (cleaned.isEmpty()) {
                continue;
            }
            lines += Math.max(1, (cleaned.length() + safeWidth - 1) / safeWidth);
        }
        return lines;
    }

    String summarize(String[] rows) {
        int words = countWords(rows);
        int longest = longestLine(rows);
        boolean hasWarning = containsPhrase(rows, "warning");
        int digitLines = countLinesWithDigits(rows);
        int wrapped = estimateWrappedLineCount(rows, 40);
        StringBuilder builder = new StringBuilder();
        builder.append("words=").append(words);
        builder.append(", longest=").append(longest);
        builder.append(", digits=").append(digitLines);
        builder.append(", wrapped=").append(wrapped);
        builder.append(", warning=").append(hasWarning);
        builder.append(", index=").append(buildIndex(rows));
        return builder.toString();
    }
}
