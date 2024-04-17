package org.hotswap.agent.compiler;

import org.hotswap.agent.handle.CompileEngine;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.manager.AllExtensionsManager;
import org.hotswap.agent.util.classloader.URLClassPathHelper;

import javax.tools.*;
import java.io.File;
import java.lang.reflect.Field;
import java.net.URL;
import java.util.*;

public class DynamicCompiler {
    private final JavaCompiler javaCompiler = ToolProvider.getSystemJavaCompiler();
    private final StandardJavaFileManager standardFileManager;
    private final List<String> options = new ArrayList<>();
    private final DynamicClassLoader dynamicClassLoader;

    private final Collection<JavaFileObject> compilationUnits = new ArrayList<JavaFileObject>();
    private final List<Diagnostic<? extends JavaFileObject>> errors = new ArrayList<Diagnostic<? extends JavaFileObject>>();
    private final List<Diagnostic<? extends JavaFileObject>> warnings = new ArrayList<Diagnostic<? extends JavaFileObject>>();

    private static final AgentLogger LOGGER = AgentLogger.getLogger(CompileEngine.class);

    public DynamicCompiler(ClassLoader classLoader) {
        if (javaCompiler == null) {
            throw new IllegalStateException(
                    "Can not load JavaCompiler from javax.tools.ToolProvider#getSystemJavaCompiler(),"
                            + " please confirm the application running in JDK not JRE.");
        }
        standardFileManager = javaCompiler.getStandardFileManager(null, null, null);

        options.add("-Xlint:unchecked");
        options.add("-g");

        List<URL> urlList = new ArrayList<>();

        //添加自定义jar资源
        urlList.addAll(getCustomJarUrl());
        //获取userClassLoader加载的资源（SpringBoot服务 LaunchedURLClassLoader）
        urlList.addAll(getClassLoaderUrl(classLoader));

        // 向上查找父类
        ClassLoader appClassLoader = classLoader.getParent();
        dynamicClassLoader = new DynamicClassLoader(urlList.toArray(new URL[0]), appClassLoader);
    }

    private List<URL> getCustomJarUrl() {
        List<URL> result = new ArrayList<>();
        File lombokJar = new File(AllExtensionsManager.getInstance().getBaseDirPath(), "lombok.jar");
        try {
            result.add(lombokJar.toURI().toURL());
        } catch (Exception e) {
            LOGGER.error("custom jar add to classpath failure", e);
        }
        return result;
    }

    private List<URL> getClassLoaderUrl(ClassLoader classLoader) {
        try {
            Field ucpField = URLClassPathHelper.getUcpField(classLoader);
            if (ucpField == null) {
                LOGGER.error("Unable to find ucp field in classLoader {}", classLoader);
                return new ArrayList<>();
            }
            ucpField.setAccessible(true);
            URL[] origClassPath = URLClassPathHelper.getOrigClassPath(classLoader, ucpField);
            return Arrays.asList(origClassPath);
        } catch (Exception e) {
            LOGGER.error("getClassLoaderUrl failure", e);
            return new ArrayList<>();
        }
    }

    public void addSource(String className, String source) {
        addSource(new StringSource(className, source));
    }

    public void addSource(JavaFileObject javaFileObject) {
        compilationUnits.add(javaFileObject);
    }

    public Map<String, Class<?>> build() {

        errors.clear();
        warnings.clear();

        JavaFileManager fileManager = new DynamicJavaFileManager(standardFileManager, dynamicClassLoader);

        DiagnosticCollector<JavaFileObject> collector = new DiagnosticCollector<JavaFileObject>();
        JavaCompiler.CompilationTask task = javaCompiler.getTask(null, fileManager, collector, options, null,
                compilationUnits);

        try {

            if (!compilationUnits.isEmpty()) {
                boolean result = task.call();

                if (!result || collector.getDiagnostics().size() > 0) {

                    for (Diagnostic<? extends JavaFileObject> diagnostic : collector.getDiagnostics()) {
                        switch (diagnostic.getKind()) {
                            case NOTE:
                            case MANDATORY_WARNING:
                            case WARNING:
                                warnings.add(diagnostic);
                                break;
                            case OTHER:
                            case ERROR:
                            default:
                                errors.add(diagnostic);
                                break;
                        }

                    }

                    if (!errors.isEmpty()) {
                        throw new DynamicCompilerException("Compilation Error", errors);
                    }
                }
            }

            return dynamicClassLoader.getClasses();
        } catch (Throwable e) {
            throw new DynamicCompilerException(e, errors);
        } finally {
            compilationUnits.clear();

        }

    }

    public Map<String, byte[]> buildByteCodes() {

        errors.clear();
        warnings.clear();

        JavaFileManager fileManager = new DynamicJavaFileManager(standardFileManager, dynamicClassLoader);

        DiagnosticCollector<JavaFileObject> collector = new DiagnosticCollector<JavaFileObject>();
        JavaCompiler.CompilationTask task = javaCompiler.getTask(null, fileManager, collector, options, null,
                compilationUnits);

        try {

            if (!compilationUnits.isEmpty()) {
                boolean result = task.call();

                if (!result || collector.getDiagnostics().size() > 0) {

                    for (Diagnostic<? extends JavaFileObject> diagnostic : collector.getDiagnostics()) {
                        switch (diagnostic.getKind()) {
                            case NOTE:
                            case MANDATORY_WARNING:
                            case WARNING:
                                warnings.add(diagnostic);
                                break;
                            case OTHER:
                            case ERROR:
                            default:
                                errors.add(diagnostic);
                                break;
                        }

                    }

                    if (!errors.isEmpty()) {
                        throw new DynamicCompilerException("Compilation Error", errors);
                    }
                }
            }

            return dynamicClassLoader.getByteCodes();
        } catch (ClassFormatError e) {
            throw new DynamicCompilerException(e, errors);
        } finally {
            compilationUnits.clear();

        }

    }

    private List<String> diagnosticToString(List<Diagnostic<? extends JavaFileObject>> diagnostics) {

        List<String> diagnosticMessages = new ArrayList<String>();

        for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics) {
            diagnosticMessages.add(
                    "line: " + diagnostic.getLineNumber() + ", message: " + diagnostic.getMessage(Locale.US));
        }

        return diagnosticMessages;

    }

    public List<String> getErrors() {
        return diagnosticToString(errors);
    }

    public List<String> getWarnings() {
        return diagnosticToString(warnings);
    }

    public ClassLoader getClassLoader() {
        return dynamicClassLoader;
    }
}
