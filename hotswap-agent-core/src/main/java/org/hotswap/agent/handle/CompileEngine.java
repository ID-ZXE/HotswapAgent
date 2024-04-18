package org.hotswap.agent.handle;

import org.hotswap.agent.compiler.DynamicCompiler;
import org.apache.commons.io.FileUtils;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.manager.AllExtensionsManager;
import org.hotswap.agent.util.classloader.URLClassPathHelper;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;

public class CompileEngine {

    private static final AgentLogger LOGGER = AgentLogger.getLogger(CompileEngine.class);

    private static final CompileEngine INSTANCE = new CompileEngine();

    private volatile Map<Class<?>, byte[]> reloadMap = null;

    private static volatile boolean addedToolsJar = false;

    public static CompileEngine getInstance() {
        return INSTANCE;
    }

    public long compile() throws Exception {
        long start = System.currentTimeMillis();
        StaticFieldHandler.generateStaticFieldInitMethod(getJavaFile());
        doCompile();
        return System.currentTimeMillis() - start;
    }

    private void addToolsJar() {
        if (addedToolsJar) {
            return;
        }
        addedToolsJar = true;
        try {
            File toolsJar = new File(AllExtensionsManager.getInstance().getBaseDirPath(), "tools.jar");
            ClassLoader appClassLoader = AllExtensionsManager.getInstance().getClassLoader().getParent();
            URLClassPathHelper.prependClassPath(appClassLoader, new URL[]{toolsJar.toURI().toURL()});
        } catch (Exception ignore) {
        }
    }

    private void doCompile() throws Exception {
        addToolsJar();
        // 需要每次创建一个新的DynamicCompiler
        DynamicCompiler dynamicCompiler = new DynamicCompiler(AllExtensionsManager.getInstance().getClassLoader());
        long start = System.currentTimeMillis();
        List<File> javaFile = getJavaFile();
        LOGGER.info("compile {}", javaFile.stream().map(File::getName).collect(Collectors.joining(",")));
        for (File file : javaFile) {
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
            File byteCodeFile = new File(AllExtensionsManager.getInstance().getExtraClassPath(), entry.getKey().replace('.', '/').concat(".class"));
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
        LOGGER.info("远程编译结束 耗时:{}", System.currentTimeMillis() - start);
    }

    public Map<Class<?>, byte[]> getCompileResult() {
        return this.reloadMap;
    }

    public void cleanOldClassFile() {
        File classPathDir = new File(AllExtensionsManager.getInstance().getExtraClassPath());
        if (!classPathDir.exists()) {
            LOGGER.info("创建extClassPath目录:{}", AllExtensionsManager.getInstance().getExtraClassPath());
            boolean mkdirs = classPathDir.mkdirs();
        }
        File sourceDir = new File(AllExtensionsManager.getInstance().getSourceDirPath());
        if (!sourceDir.exists()) {
            LOGGER.info("创建source目录:{}", AllExtensionsManager.getInstance().getSourceDirPath());
            boolean mkdirs = sourceDir.mkdirs();
        }

        try {
            File classDir = new File(AllExtensionsManager.getInstance().getExtraClassPath());
            FileUtils.cleanDirectory(classDir);
            LOGGER.info("clean old class file");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public List<File> getJavaFile() {
        File dir = new File(AllExtensionsManager.getInstance().getSourceDirPath());
        Collection<File> fileCollection = FileUtils.listFiles(dir, new String[]{"java"}, true);
        return new ArrayList<>(fileCollection);
    }

}
