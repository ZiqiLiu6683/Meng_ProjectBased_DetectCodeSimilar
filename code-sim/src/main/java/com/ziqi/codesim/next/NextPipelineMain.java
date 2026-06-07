package com.ziqi.codesim.next;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class NextPipelineMain {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("Usage: NextPipelineMain <left-java-file> <right-java-file>");
            System.exit(2);
        }
        String left = Files.readString(Path.of(args[0]), StandardCharsets.UTF_8);
        String right = Files.readString(Path.of(args[1]), StandardCharsets.UTF_8);
        NextPipelineResult result = new NextPipelineRunner().run(left, right);
        System.out.print(new NextJsonReportFormatter().format(result));
    }
}
