package com.ziqi.codesim.next.semantic;

import com.ziqi.codesim.next.NextBreakdownReportFormatter;
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
                    "Usage: WalaNextPipelineMain <left-java-file> <right-java-file> [--json] [--report json|breakdown] [--view method|block|both] [--show-all]");
            System.exit(2);
        }
        boolean emitJson = false;
        String reportMode = "text";
        String view = "both";
        boolean showAll = false;
        for (int i = 2; i < args.length; i++) {
            String arg = args[i];
            if ("--json".equals(arg)) {
                emitJson = true;
            } else if ("--report".equals(arg) && i + 1 < args.length) {
                reportMode = args[++i];
            } else if (arg.startsWith("--report=")) {
                reportMode = arg.substring("--report=".length());
            } else if ("--view".equals(arg) && i + 1 < args.length) {
                view = args[++i];
            } else if (arg.startsWith("--view=")) {
                view = arg.substring("--view=".length());
            } else if ("--show-all".equals(arg)) {
                showAll = true;
            }
        }
        Path leftPath = Path.of(args[0]);
        Path rightPath = Path.of(args[1]);
        String left = Files.readString(leftPath, StandardCharsets.UTF_8);
        String right = Files.readString(rightPath, StandardCharsets.UTF_8);
        NextPipelineResult result = new WalaNextPipelineRunner().run(left, right);
        if (emitJson || "json".equalsIgnoreCase(reportMode)) {
            // Same JSON shape as NextPipelineMain so batch evaluation tooling can
            // parse the WALA (CFG-on) engine exactly like the source-only engine.
            System.out.print(new NextJsonReportFormatter().format(result));
            return;
        }
        if ("breakdown".equalsIgnoreCase(reportMode)) {
            // CFG-on breakdown: same per-file, per-type report, with the cfg-sim raw numbers
            // populated because this engine attaches the structural (MCS) oracle.
            System.out.print(new NextBreakdownReportFormatter().format(
                    result,
                    leftPath.getFileName().toString(),
                    rightPath.getFileName().toString(),
                    view,
                    showAll));
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
