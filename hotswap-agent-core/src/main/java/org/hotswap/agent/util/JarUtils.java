package org.hotswap.agent.util;


import org.hotswap.agent.constants.HotswapConstants;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class JarUtils {

    public static final String LOMBOK = "lombok.jar";

    public static void createLombokJar() {
        try {
            Path sourceFile = Paths.get(HotswapConstants.BASE_PATH + "/" + LOMBOK);
            Path targetFile = Paths.get(HotswapConstants.EXT_CLASS_PATH + LOMBOK);
            Files.copy(sourceFile, targetFile);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
