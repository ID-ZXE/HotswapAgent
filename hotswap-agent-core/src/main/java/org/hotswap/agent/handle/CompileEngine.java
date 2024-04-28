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

    private volatile boolean isCompiling = false;

    private volatile boolean addedToolsJar = false;

    public static CompileEngine getInstance() {
        return INSTANCE;
    }

    public long compile() throws Exception {
        isCompiling = true;
        long start = System.currentTimeMillis();
        try {
            StaticFieldHandler.generateStaticFieldInitMethod(getJavaFile());
            doCompile();
        } finally {
            isCompiling = false;
            cleanSourceFile();
        }
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
        } catch (Exception e) {
            throw new RuntimeException("tools.jar初始化失败", e);
        }
    }

    private void doCompile() throws Exception {
        LOGGER.info("Java远程编译器初始化");
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

        Map<String, byte[]> byteCodes;
        try {
            byteCodes = dynamicCompiler.buildByteCodes();
        } catch (Exception e) {
            LOGGER.error("编译失败", e);
            throw e;
        }

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

    public void setCompileResult(Map<Class<?>, byte[]> reloadMap) {
        this.reloadMap = reloadMap;
    }

    public void cleanOldClassFile() {
        File basePathDir = new File(AllExtensionsManager.getInstance().getBaseDirPath());
        if (!basePathDir.exists()) {
            LOGGER.info("创建base目录:{}", AllExtensionsManager.getInstance().getBaseDirPath());
            boolean mkdirs = basePathDir.mkdirs();
        }
        File classPathDir = new File(AllExtensionsManager.getInstance().getExtraClassPath());
        if (!classPathDir.exists()) {
            boolean mkdirs = classPathDir.mkdirs();
            LOGGER.info("创建extClassPath目录:{} result:{}", AllExtensionsManager.getInstance().getExtraClassPath(), mkdirs);
        }
        File sourceDir = new File(AllExtensionsManager.getInstance().getSourceDirPath());
        if (!sourceDir.exists()) {
            boolean mkdirs = sourceDir.mkdirs();
            LOGGER.info("创建source目录:{} result:{}", AllExtensionsManager.getInstance().getSourceDirPath(), mkdirs);
        }
        File jarDir = new File(AllExtensionsManager.getInstance().getJarDirPath());
        if (!jarDir.exists()) {
            boolean mkdirs = jarDir.mkdirs();
            LOGGER.info("创建jar目录:{} result:{}", AllExtensionsManager.getInstance().getJarDirPath(), mkdirs);
        }

        try {
            FileUtils.cleanDirectory(classPathDir);
            FileUtils.cleanDirectory(sourceDir);
            FileUtils.cleanDirectory(jarDir);
            LOGGER.info("clean old class file");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void cleanSourceFile() {
        try {
            File sourceDir = new File(AllExtensionsManager.getInstance().getSourceDirPath());
            FileUtils.cleanDirectory(sourceDir);
            LOGGER.info("clean source file");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public List<File> getJavaFile() {
        File dir = new File(AllExtensionsManager.getInstance().getSourceDirPath());
        Collection<File> fileCollection = FileUtils.listFiles(dir, new String[]{"java"}, true);
        return new ArrayList<>(fileCollection);
    }

    public boolean isCompiling() {
        return isCompiling;
    }

    public void setIsCompiling(boolean isCompiling) {
        this.isCompiling = isCompiling;
    }

}
