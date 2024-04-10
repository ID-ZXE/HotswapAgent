package org.hotswap.agent.util;


import org.apache.commons.io.FileUtils;
import org.hotswap.agent.constants.HotswapConstants;
import org.hotswap.agent.logging.AgentLogger;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class JarUtils {

    private static final AgentLogger LOGGER = AgentLogger.getLogger(JarUtils.class);

    private static final String LOMBOK = "lombok.jar";

    private static File createJarFile(File file, String filename) throws IOException {
        try {
            InputStream inputStream = ClassLoader.getSystemResources(filename).nextElement().openStream();
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

            File file = new File(HotswapConstants.EXT_CLASS_PATH, LOMBOK);
            if (!file.exists()) {
                boolean newFile = file.createNewFile();
            }
            return JarUtils.createJarFile(file, LOMBOK);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
