package org.hotswap.agent.handle;

import com.taobao.arthas.compiler.DynamicCompiler;
import jdk.nashorn.internal.ir.ReturnNode;
import org.apache.commons.io.FileUtils;
import org.hotswap.agent.config.PluginConfiguration;
import org.hotswap.agent.config.PluginManager;
import org.hotswap.agent.constants.HotswapConstants;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.util.JarUtils;
import org.hotswap.agent.util.classloader.URLClassPathHelper;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.*;

public class CompileEngine {

    private static final AgentLogger LOGGER = AgentLogger.getLogger(CompileEngine.class);

    private static final CompileEngine INSTANCE = new CompileEngine();

    private volatile DynamicCompiler dynamicCompiler;

    private volatile Map<Class<?>, byte[]> reloadMap = null;

    public static CompileEngine getInstance() {
        return INSTANCE;
    }

    public void compile() throws Exception {
        StaticFieldHandler.generateStaticFieldInitMethod(getJavaFile());
        compile(getCompiler());
    }

    private void compile(DynamicCompiler dynamicCompiler) throws Exception {
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

        // 全部写入文件系统
        for (Map.Entry<String, byte[]> entry : byteCodes.entrySet()) {
            File byteCodeFile = new File(HotswapConstants.EXT_CLASS_PATH, entry.getKey().replace('.', '/').concat(".class"));
            FileUtils.writeByteArrayToFile(byteCodeFile, entry.getValue(), false);
        }

        // load class
        this.reloadMap = new HashMap<>();
        for (Map.Entry<String, byte[]> entry : byteCodes.entrySet()) {
            Class<?> clazz;
            try {
                clazz = AllExtensionsManager.getInstance().getClassLoader().loadClass(entry.getKey());
            } catch (ClassNotFoundException e) {
                LOGGER.error("hotswap tries to reload class {}, which is not known to application classLoader {}.", entry.getKey(), AllExtensionsManager.getInstance().getClassLoader());
                throw new RuntimeException(e);
            }
            this.reloadMap.put(clazz, entry.getValue());
        }
        LOGGER.info("compile cost {} ms", System.currentTimeMillis() - start);
    }

    public Map<Class<?>, byte[]> getCompileResult() {
        return this.reloadMap;
    }

    public void cleanCompileResult() {
        if (Objects.isNull(this.reloadMap)) {
            return;
        }
        this.reloadMap.clear();
    }

    public void cleanOldClassFile() {
        LOGGER.info("clean old class file");
        try {
            File classDir = new File(HotswapConstants.EXT_CLASS_PATH);
            FileUtils.cleanDirectory(classDir);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public List<File> getJavaFile() {
        File dir = new File(HotswapConstants.SOURCE_FILE_PATH);
        Collection<File> fileCollection = FileUtils.listFiles(dir, new String[]{"java"}, true);
        return new ArrayList<>(fileCollection);
    }

    private DynamicCompiler getCompiler() {
        if (dynamicCompiler == null) {
            synchronized (CompileEngine.class) {
                if (dynamicCompiler == null) {
                    PluginConfiguration.initExtraClassPath(AllExtensionsManager.getInstance().getClassLoader());
                    try {
                        File lombokJar = JarUtils.createLombokJar();
                        URLClassPathHelper.prependClassPath(AllExtensionsManager.getInstance().getClassLoader(), new URL[]{lombokJar.toURI().toURL()});
                    } catch (Exception e) {
                        LOGGER.error("createLombokJar error", e);
                    }
                    dynamicCompiler = new DynamicCompiler(AllExtensionsManager.getInstance().getClassLoader());
                }
            }
        }
        return dynamicCompiler;
    }

}
