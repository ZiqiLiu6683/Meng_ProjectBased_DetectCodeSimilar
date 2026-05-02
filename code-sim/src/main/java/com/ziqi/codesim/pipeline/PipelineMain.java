package com.ziqi.codesim.pipeline;

import com.ziqi.codesim.io.FileUtils;

public class PipelineMain {
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("Usage: mvn -q exec:java "
                    + "-Dexec.mainClass=\"com.ziqi.codesim.pipeline.PipelineMain\" "
                    + "-Dexec.args=\"A.java B.java\"");
            return;
        }

        String sourceA = FileUtils.readAll(args[0]);
        String sourceB = FileUtils.readAll(args[1]);
        FullPipelineResult result = new PipelineRunner().runFull(sourceA, sourceB);
        String report = new TextReportFormatter().format(result, args[0], args[1]);
        System.out.print(report);
    }
}
