package com.ziqi.codesim.pipeline;

import com.ziqi.codesim.io.FileUtils;

public class PipelineMain {
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("Usage: mvn -q exec:java "
                    + "-Dexec.mainClass=\"com.ziqi.codesim.pipeline.PipelineMain\" "
                    + "-Dexec.args=\"[--json] A.java B.java\"");
            return;
        }

        boolean json = "--json".equals(args[0]);
        int offset = json ? 1 : 0;
        if (args.length - offset < 2) {
            System.out.println("Usage: mvn -q exec:java "
                    + "-Dexec.mainClass=\"com.ziqi.codesim.pipeline.PipelineMain\" "
                    + "-Dexec.args=\"[--json] A.java B.java\"");
            return;
        }

        String fileA = args[offset];
        String fileB = args[offset + 1];
        String sourceA = FileUtils.readAll(fileA);
        String sourceB = FileUtils.readAll(fileB);
        FullPipelineResult result = new PipelineRunner().runFull(sourceA, sourceB);
        String report = json
                ? new JsonReportFormatter().format(result, fileA, fileB)
                : new TextReportFormatter().format(result, fileA, fileB);
        System.out.print(report);
    }
}
