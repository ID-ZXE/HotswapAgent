package org.hotswap.agent.handle;

import com.taobao.arthas.compiler.DynamicCompiler;
import org.apache.commons.io.FileUtils;
import org.hotswap.agent.constants.HotswapConstants;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.watch.nio.AbstractNIO2Watcher;

import java.io.File;
import java.lang.instrument.Instrumentation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class LocalCompileHandler {

    private static volatile DynamicCompiler dynamicCompiler;

    private static AbstractNIO2Watcher watcher;

    private static String extraClasspath;

    private static ClassLoader classLoader;

    private static Instrumentation instrumentation;

    private static AgentLogger LOGGER = AgentLogger.getLogger(LocalCompileHandler.class);

    public static void init(AbstractNIO2Watcher watcher, String extraClasspath, Instrumentation instrumentation) {
        LocalCompileHandler.watcher = watcher;
        LocalCompileHandler.extraClasspath = extraClasspath;
        LocalCompileHandler.instrumentation = instrumentation;
    }

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

        for (Map.Entry<String, byte[]> entry : byteCodes.entrySet()) {
            File byteCodeFile = new File(extraClasspath, entry.getKey().replace('.', '/').concat(".class"));
            String classDestinationPath = Paths.get(extraClasspath, entry.getKey().replace('.', '/').concat(".class")).toString();
            Path destinationPath = Paths.get(classDestinationPath);
            if (!Files.exists(destinationPath.getParent())) {
                Path directories = Files.createDirectories(destinationPath.getParent());
                watcher.addDirectory(directories);
            }
            FileUtils.writeByteArrayToFile(byteCodeFile, entry.getValue(), false);
        }

        LOGGER.info("compile cost {} ms", System.currentTimeMillis() - start);
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
                    initCompileClassLoader(instrumentation, HotswapConstants.LAUNCHED_CLASS_LOADER);
                    dynamicCompiler = new DynamicCompiler(classLoader);
                }
            }
        }
        return dynamicCompiler;
    }

    private static void initCompileClassLoader(Instrumentation inst, String classLoaderName) {
        // 获取所有已加载的类
        Class<?>[] loadedClasses = inst.getAllLoadedClasses();
        // 找出 LaunchedURLClassLoader
        classLoader = Arrays.stream(loadedClasses).filter(c -> c.getClassLoader() != null && c.getClassLoader().getClass() != null).map(Class::getClassLoader).filter(classLoader -> classLoaderName.equals(classLoader.getClass().getName())).findFirst().orElse(Thread.currentThread().getContextClassLoader());
        LOGGER.info("compileClassLoader: {}", classLoader);
    }

}
