// Ziqi Liu Meng Project-Based Software Engineering
// This file is summary of the file functions
// Functions include: readAll
package com.ziqi.codesim.io;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class FileUtils {
    public static String readAll(String filePath) throws Exception {
        return new String(Files.readAllBytes(Paths.get(filePath)), StandardCharsets.UTF_8);
    }
}


