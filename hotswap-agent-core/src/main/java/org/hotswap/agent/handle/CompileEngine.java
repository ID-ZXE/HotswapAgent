package org.hotswap.agent.handle;

import com.taobao.arthas.compiler.DynamicCompiler;
import org.apache.commons.io.FileUtils;
import org.hotswap.agent.constants.HotswapConstants;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.manager.AllExtensionsManager;
import org.hotswap.agent.watch.nio.AbstractNIO2Watcher;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class CompileEngine {

    private static final AgentLogger LOGGER = AgentLogger.getLogger(CompileEngine.class);

    private static final CompileEngine INSTANCE = new CompileEngine();

    private volatile DynamicCompiler dynamicCompiler;

    private volatile Map<Class<?>, byte[]> reloadMap = null;

    private AbstractNIO2Watcher watcher;

    public static CompileEngine getInstance() {
        return INSTANCE;
    }

    public void compile() throws Exception {
        LombokHandler.deLombok(getJavaFile());
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
            // 主动监控
            Path destinationPath = Paths.get(byteCodeFile.getAbsolutePath());
            if (!Files.exists(destinationPath.getParent())) {
                Path directories = Files.createDirectories(destinationPath.getParent());
                watcher.addDirectory(directories);
            }
            FileUtils.writeByteArrayToFile(byteCodeFile, entry.getValue(), false);
        }

        // load class
        this.reloadMap = new HashMap<>();
        LOGGER.info("远程编译结束 耗时:{}", System.currentTimeMillis() - start);
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
                    ClassLoader compilerClassLoader = AllExtensionsManager.getInstance().getCompilerClassLoader();
                    LOGGER.info("compiler class loader:{}", compilerClassLoader);
                    dynamicCompiler = new DynamicCompiler(compilerClassLoader);
                }
            }
        }
        return dynamicCompiler;
    }

    public void setWatcher(AbstractNIO2Watcher watcher) {
        this.watcher = watcher;
    }
}
