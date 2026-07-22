package com.ziqi.codesim.next.semantic.eval;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfSystemProperty(named = "semantic.tests.enabled", matches = "true")
class BatchPairMainSchemaTest {

    @TempDir
    Path temp;

    @Test
    void writesVersionedExecutionProvenance() throws Exception {
        String previous = System.getProperty("codesim.skipDynamic");
        System.setProperty("codesim.skipDynamic", "true");
        try {
            Path left = temp.resolve("Left.java");
            Path right = temp.resolve("Right.java");
            Path manifest = temp.resolve("manifest.csv");
            Path output = temp.resolve("results.jsonl");
            Files.writeString(left, "class Left { int f(int x) { return x + 1; } }",
                    StandardCharsets.UTF_8);
            Files.writeString(right, "class Right { int g(int y) { return y + 1; } }",
                    StandardCharsets.UTF_8);
            Files.writeString(manifest,
                    "pair_id,left_path,right_path\ncase_1," + left + "," + right + "\n",
                    StandardCharsets.UTF_8);

            BatchPairMain.main(new String[]{manifest.toString(), output.toString()});

            String json = Files.readString(output, StandardCharsets.UTF_8);
            assertTrue(json.contains("\"schemaVersion\":\"2.0-dev\""));
            assertTrue(json.contains("\"analysisMode\":\"SOURCE_PLUS_WALA_SMT\""));
            assertTrue(json.contains("\"compile_left\":{\"status\":\"SUCCESS\""));
            assertTrue(json.contains("\"durationMs\":"));
            assertTrue(json.contains("\"leftSha256\":"));
            assertTrue(json.contains("\"rightSha256\":"));
        } finally {
            if (previous == null) {
                System.clearProperty("codesim.skipDynamic");
            } else {
                System.setProperty("codesim.skipDynamic", previous);
            }
        }
    }
}
