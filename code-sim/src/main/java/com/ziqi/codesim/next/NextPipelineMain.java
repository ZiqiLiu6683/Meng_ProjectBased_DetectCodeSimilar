package com.ziqi.codesim.next;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class NextPipelineMain {
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: NextPipelineMain <left-java-file> <right-java-file> [--report json|breakdown]");
            System.exit(2);
        }
        // Default stays json so existing batch-evaluation tooling that parses stdout
        // as JSON is unaffected; breakdown prints the per-file, per-type human report.
        String reportMode = "json";
        for (int i = 2; i < args.length; i++) {
            if ("--report".equals(args[i]) && i + 1 < args.length) {
                reportMode = args[++i];
            } else if (args[i].startsWith("--report=")) {
                reportMode = args[i].substring("--report=".length());
            }
        }
        Path leftPath = Path.of(args[0]);
        Path rightPath = Path.of(args[1]);
        String left = Files.readString(leftPath, StandardCharsets.UTF_8);
        String right = Files.readString(rightPath, StandardCharsets.UTF_8);
        NextPipelineResult result = new NextPipelineRunner().run(left, right);
        if ("breakdown".equalsIgnoreCase(reportMode)) {
            System.out.print(new NextBreakdownReportFormatter().format(
                    result,
                    leftPath.getFileName().toString(),
                    rightPath.getFileName().toString()));
        } else {
            System.out.print(new NextJsonReportFormatter().format(result));
        }
    }
}
