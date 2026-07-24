package com.ziqi.codesim.next.semantic.eval;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
            assertTrue(json.contains("\"schemaVersion\":\"4.0\""));
            assertTrue(json.contains("\"analysisMode\":\"SOURCE_PLUS_WALA_SMT\""));
            assertTrue(json.contains("\"compile_left\":{\"status\":\"SUCCESS\""));
            assertTrue(json.contains("\"durationMs\":"));
            assertTrue(json.contains("\"leftSha256\":"));
            assertTrue(json.contains("\"rightSha256\":"));
            assertTrue(json.contains("\"compilations\":{"));
            assertTrue(json.contains("\"javaRelease\":17"));
        } finally {
            if (previous == null) {
                System.clearProperty("codesim.skipDynamic");
            } else {
                System.setProperty("codesim.skipDynamic", previous);
            }
        }
    }

    @Test
    void readsPortableQuotedManifestAndResumeIsIdempotent() throws Exception {
        String previous = System.getProperty("codesim.skipDynamic");
        System.setProperty("codesim.skipDynamic", "true");
        try {
            Path pairDirectory = temp.resolve("pair,portable");
            Files.createDirectories(pairDirectory);
            Path left = pairDirectory.resolve("Left.java");
            Path right = pairDirectory.resolve("Right.java");
            Path manifest = temp.resolve("manifest-v2.csv");
            Path output = temp.resolve("results-v2.jsonl");
            String leftSource = "class Left { int f(int x) { return x + 1; } }";
            String rightSource = "class Right { int g(int y) { return y + 1; } }";
            Files.writeString(left, leftSource, StandardCharsets.UTF_8);
            Files.writeString(right, rightSource, StandardCharsets.UTF_8);
            Files.writeString(manifest,
                    "schema_version,dataset_id,pair_id,left_path,right_path,left_sha256,right_sha256\n"
                            + "execution-1.0,bcb-smoke,portable_1,\"pair,portable/Left.java\","
                            + "\"pair,portable/Right.java\"," + sha256(leftSource) + ","
                            + sha256(rightSource) + "\n",
                    StandardCharsets.UTF_8);

            BatchPairMain.main(new String[]{manifest.toString(), output.toString()});
            BatchPairMain.main(new String[]{manifest.toString(), output.toString()});

            String json = Files.readString(output, StandardCharsets.UTF_8);
            assertTrue(json.contains("\"datasetId\":\"bcb-smoke\""));
            assertTrue(json.contains("\"manifestSha256\":"));
            assertTrue(json.contains("\"manifestRowSha256\":"));
            assertEquals(1, Files.readAllLines(output, StandardCharsets.UTF_8).size());
        } finally {
            if (previous == null) {
                System.clearProperty("codesim.skipDynamic");
            } else {
                System.setProperty("codesim.skipDynamic", previous);
            }
        }
    }

    private static String sha256(String value) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder();
        for (byte b : digest) {
            hex.append(String.format("%02x", b & 0xff));
        }
        return hex.toString();
    }
}
