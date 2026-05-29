package com.ziqi.codesim.semantic.backend.wala.raw;

import com.ziqi.codesim.semantic.raw.RawToolJsonlWriter;
import com.ziqi.codesim.semantic.raw.RawToolProgram;

import java.nio.file.Path;

public class WalaRawSnapshotMain {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("Usage: WalaRawSnapshotMain <class-dir-or-jar> <output-jsonl>");
            System.exit(2);
        }
        Path input = Path.of(args[0]);
        Path output = Path.of(args[1]);
        RawToolProgram snapshot = new WalaRawSnapshotExtractor().extract(input);
        new RawToolJsonlWriter().writeRecords(output, snapshot);
        System.out.println("Wrote WALA raw snapshot records to " + output.toAbsolutePath());
    }
}
