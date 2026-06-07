package com.ziqi.codesim.next.semantic;

import com.ziqi.codesim.next.NextPipelineResult;
import com.ziqi.codesim.next.RegionDecision;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class WalaNextPipelineMain {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("Usage: WalaNextPipelineMain <left-java-file> <right-java-file>");
            System.exit(2);
        }
        String left = Files.readString(Path.of(args[0]), StandardCharsets.UTF_8);
        String right = Files.readString(Path.of(args[1]), StandardCharsets.UTF_8);
        NextPipelineResult result = new WalaNextPipelineRunner().run(left, right);
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
