package com.ziqi.codesim.next.semantic;

import com.ziqi.codesim.next.NextJsonReportFormatter;
import com.ziqi.codesim.next.NextPipelineResult;
import com.ziqi.codesim.next.RegionDecision;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class WalaNextPipelineMain {
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println(
                    "Usage: WalaNextPipelineMain <left-java-file> <right-java-file> [--json]");
            System.exit(2);
        }
        boolean emitJson = false;
        for (int i = 2; i < args.length; i++) {
            if ("--json".equals(args[i])) {
                emitJson = true;
            }
        }
        String left = Files.readString(Path.of(args[0]), StandardCharsets.UTF_8);
        String right = Files.readString(Path.of(args[1]), StandardCharsets.UTF_8);
        NextPipelineResult result = new WalaNextPipelineRunner().run(left, right);
        if (emitJson) {
            // Same JSON shape as NextPipelineMain so batch evaluation tooling can
            // parse the WALA (CFG-on) engine exactly like the source-only engine.
            System.out.print(new NextJsonReportFormatter().format(result));
            return;
        }
        System.out.println("candidates=" + result.candidates().size());
        for (RegionDecision decision : result.regionDecisions()) {
            boolean cfg = decision.candidate().sources().stream()
                    .anyMatch(source -> source.channel().equals("CFG_KNN_SCAN"));
            if (!cfg && decision.type().name().equals("NON_CLONE")) {
                continue;
            }
            System.out.printf(
                    "%s -> %s | type=%s strength=%s sim=%.4f sources=%s%n",
                    decision.candidate().left().displayName(),
                    decision.candidate().right().displayName(),
                    decision.type(),
                    decision.strength(),
                    decision.syntacticSimilarity(),
                    decision.candidate().sources()
            );
        }
    }
}
