package com.ziqi.codesim.semantic.raw;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class RawToolJsonlWriter {
    public void writeRecords(Path output, RawToolProgram program) throws IOException {
        Files.createDirectories(output.toAbsolutePath().getParent());
        List<RawToolRecord> records = new RawToolRecordCollector().collect(program);
        try (BufferedWriter writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8)) {
            for (RawToolRecord record : records) {
                writer.write(toJson(program, record));
                writer.newLine();
            }
        }
    }

    private static String toJson(RawToolProgram program, RawToolRecord record) {
        return "{"
                + "\"toolName\":" + quote(program.toolName()) + ","
                + "\"toolVersion\":" + quote(program.toolVersion()) + ","
                + "\"inputId\":" + quote(program.inputId()) + ","
                + "\"channel\":" + quote(record.channel()) + ","
                + "\"rawValue\":" + quote(record.rawValue()) + ","
                + "\"provenance\":" + toJsonArray(record.provenance())
                + "}";
    }

    private static String toJsonArray(List<String> values) {
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) builder.append(',');
            builder.append(quote(values.get(i)));
        }
        return builder.append(']').toString();
    }

    private static String quote(String value) {
        StringBuilder builder = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> builder.append("\\\"");
                case '\\' -> builder.append("\\\\");
                case '\b' -> builder.append("\\b");
                case '\f' -> builder.append("\\f");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default -> {
                    if (c < 0x20) {
                        builder.append(String.format("\\u%04x", (int) c));
                    } else {
                        builder.append(c);
                    }
                }
            }
        }
        return builder.append('"').toString();
    }
}
