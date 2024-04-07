package org.hotswap.agent.util;


import org.apache.commons.io.FileUtils;
import org.hotswap.agent.constants.HotswapConstants;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class JarUtils {

    private static File createJarFile(File file, String filename) throws IOException {
        try {
            InputStream inputStream = JarUtils.class.getClassLoader().getResourceAsStream(filename);
            FileOutputStream fos = new FileOutputStream(file);
            byte[] b = new byte[1024];
            int length;
            while ((length = inputStream.read(b)) > 0) {
                fos.write(b, 0, length);
            }
            inputStream.close();
            fos.close();
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
