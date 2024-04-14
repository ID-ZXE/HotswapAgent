package org.hotswap.agent.handle;

import com.taobao.arthas.compiler.DynamicCompiler;
import org.apache.commons.io.FileUtils;
import org.hotswap.agent.constants.HotswapConstants;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.manager.AllExtensionsManager;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class CompileEngine {

    private static final AgentLogger LOGGER = AgentLogger.getLogger(CompileEngine.class);

    private static final CompileEngine INSTANCE = new CompileEngine();

    private volatile Map<Class<?>, byte[]> reloadMap = null;

    public static CompileEngine getInstance() {
        return INSTANCE;
    }

    public void compile() throws Exception {
        LombokHandler.deLombok(getJavaFile());
        StaticFieldHandler.generateStaticFieldInitMethod(getJavaFile());
        doCompile();
    }

    private void doCompile() throws Exception {
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
        LOGGER.info("远程编译结束 耗时:{}", System.currentTimeMillis() - start);
    }

    public Map<Class<?>, byte[]> getCompileResult() {
        return this.reloadMap;
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

}
