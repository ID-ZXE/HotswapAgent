package org.hotswap.agent.util;


import org.apache.commons.io.FileUtils;
import org.hotswap.agent.constants.HotswapConstants;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class JarUtils {

    private static File createJarFile(File file, String filename) throws IOException {
        try {
            Files.copy(new File(filename).toPath(), Files.newOutputStream(file.toPath()));
            return file;
        } catch (Exception e) {
            FileUtils.forceDelete(file);
            throw e;
        }
    }

    public static File createLombokJar() {
        try {
            File dir = new File(HotswapConstants.EXT_CLASS_PATH);
            if (!dir.exists()) {
                boolean mkdirs = dir.mkdirs();
            }

            File file = new File(HotswapConstants.EXT_CLASS_PATH, "lombok.jar");
            if (!file.exists()) {
                boolean newFile = file.createNewFile();
            }
            return JarUtils.createJarFile(file, "lombok.jar");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
