package org.hotswap.agent.handle;

import com.taobao.arthas.compiler.DynamicCompiler;
import org.apache.commons.io.FileUtils;
import org.hotswap.agent.config.PluginConfiguration;
import org.hotswap.agent.config.PluginManager;
import org.hotswap.agent.constants.HotswapConstants;
import org.hotswap.agent.logging.AgentLogger;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class LocalCompileHandler {

    private static volatile DynamicCompiler dynamicCompiler;

    private static AgentLogger LOGGER = AgentLogger.getLogger(LocalCompileHandler.class);

    public static void compile() throws Exception {
        DynamicCompiler dynamicCompiler = getCompiler();
        compile(dynamicCompiler);
    }

    private static void compile(DynamicCompiler dynamicCompiler) throws Exception {
        long start = System.currentTimeMillis();
        for (File file : getJavaFile()) {
            String sourceCode = FileUtils.readFileToString(file, "UTF-8");
            String name = file.getName();
            if (name.endsWith(".java")) {
                name = name.substring(0, name.length() - ".java".length());
            }
            dynamicCompiler.addSource(name, sourceCode);
        }

        Map<String, byte[]> byteCodes = dynamicCompiler.buildByteCodes();


        Map<Class<?>, byte[]> reloadMap = new HashMap<>();
        for (Map.Entry<String, byte[]> entry : byteCodes.entrySet()) {
            File byteCodeFile = new File(HotswapConstants.EXT_CLASS_PATH, entry.getKey().replace('.', '/').concat(".class"));
            FileUtils.writeByteArrayToFile(byteCodeFile, entry.getValue(), false);
            Class<?> clazz;
            try {
                clazz = AllExtensionsManager.getClassLoader().loadClass(entry.getKey());
            } catch (ClassNotFoundException e) {
                LOGGER.error("hotswap tries to reload class {}, which is not known to application classLoader {}.",
                        entry.getKey(), AllExtensionsManager.getClassLoader());
                throw new RuntimeException(e);
            }
            reloadMap.put(clazz, entry.getValue());
        }
        if (!reloadMap.isEmpty()) {
            PluginManager.getInstance().hotswap(reloadMap);
        }
        LOGGER.info("compile cost {} ms", System.currentTimeMillis() - start);
    }

    public static void cleanOldClassFile() {
        LOGGER.info("clean old class file");
        try {
            File classDir = new File(HotswapConstants.EXT_CLASS_PATH);
            FileUtils.cleanDirectory(classDir);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static List<File> getJavaFile() {
        File dir = new File(HotswapConstants.SOURCE_FILE_PATH);
        Collection<File> fileCollection = FileUtils.listFiles(dir, new String[]{"java"}, true);
        return new ArrayList<>(fileCollection);
    }

    private static DynamicCompiler getCompiler() {
        if (dynamicCompiler == null) {
            synchronized (LocalCompileHandler.class) {
                if (dynamicCompiler == null) {
                     PluginConfiguration.initExtraClassPath(AllExtensionsManager.getClassLoader());
                    dynamicCompiler = new DynamicCompiler(AllExtensionsManager.getClassLoader());
                }
            }
        }
        return dynamicCompiler;
    }

}
