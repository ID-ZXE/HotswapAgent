package org.hotswap.agent.handle;


import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.printer.DefaultPrettyPrinter;
import org.apache.commons.io.FileUtils;
import org.hotswap.agent.logging.AgentLogger;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.concurrent.CountDownLatch;

public class StaticFieldHandler {

    private static final String STATIC_FIELD_INIT_METHOD = "$$hotswap$$_get";

    private static final AgentLogger LOGGER = AgentLogger.getLogger(StaticFieldHandler.class);

    public static void generateStaticFieldInitMethod(List<File> javaList) {
        long start = System.currentTimeMillis();
        for (File javaFile : javaList) {
            try {
                JavaParser javaParser = new JavaParser();
                //对文件进行解析操作，读入内存
                ParseResult<CompilationUnit> result = javaParser.parse(javaFile);
                if (!result.getResult().isPresent()) {
                    return;
                }
                CompilationUnit cu = result.getResult().get();
                for (Node childNode : cu.getChildNodes()) {
                    if (childNode instanceof ClassOrInterfaceDeclaration) {
                        ClassOrInterfaceDeclaration classOrInterfaceDeclaration = (ClassOrInterfaceDeclaration) childNode;
                        List<VariableDeclarator> variables = classOrInterfaceDeclaration.findAll(VariableDeclarator.class);

                        for (VariableDeclarator variable : variables) {
                            if (variable.getParentNode().toString().contains("static")) {
                                variable.getInitializer().ifPresent(init -> {
                                    String body = variable.getInitializer().get().toString();

                                    MethodDeclaration indexMethod = classOrInterfaceDeclaration.addMethod(STATIC_FIELD_INIT_METHOD + variable.getNameAsString(), com.github.javaparser.ast.Modifier.Keyword.PRIVATE, com.github.javaparser.ast.Modifier.Keyword.STATIC);
                                    indexMethod.setType(StaticJavaParser.parseType(variable.getTypeAsString()));
                                    BlockStmt blockStmt = StaticJavaParser.parseBlock("{ return " + body + "; }");
                                    indexMethod.setBody(blockStmt);
                                    variable.removeInitializer();
                                });
                            }
                        }

                        List<FieldDeclaration> fields = classOrInterfaceDeclaration.getFields();
                        for (FieldDeclaration field : fields) {
                            if (field.isFinal()) {
                                field.removeModifier(com.github.javaparser.ast.Modifier.Keyword.FINAL);
                            }
                        }
                    }
                }
                String outputStr = new DefaultPrettyPrinter().print(result.getResult().get());
                FileUtils.write(javaFile, outputStr, "UTF-8", false);
            } catch (Exception e) {
                LOGGER.error("generateStaticFieldInitMethod {} error", e, javaFile.getName());
            }
        }
        LOGGER.info("generateStaticFieldInitMethod cost:{}", System.currentTimeMillis() - start);
    }

    public static void executeStaticInitMethod(Class<?> clazz) {
        if (clazz.isEnum()) {
            return;
        }
        if (clazz.getName().contains("$Proxy") || clazz.getName().contains("$$")) {
            return;
        }

        long start = System.currentTimeMillis();
        Field[] declaredFields = clazz.getDeclaredFields();
        for (Field declaredField : declaredFields) {
            try {
                if (!Modifier.isStatic(declaredField.getModifiers())) {
                    continue;
                }

                // lombok特殊处理
                if (declaredField.getName().equals("log") && declaredField.getType().getName().startsWith("org.slf4j")) {
                    continue;
                }

                Method declaredMethod = clazz.getDeclaredMethod(STATIC_FIELD_INIT_METHOD + declaredField.getName());
                declaredMethod.setAccessible(true);
                Object result = declaredMethod.invoke(null);
                declaredField.setAccessible(true);
                declaredField.set(null, result);
                LOGGER.info("set {} {} = {}", clazz.getSimpleName(), declaredField.getName(), result);
            } catch (NoSuchMethodException e) {
                try {
                    declaredField.setAccessible(true);
                    declaredField.set(null, null);
                    LOGGER.info("set {} {} = null", clazz.getSimpleName(), declaredField.getName());
                } catch (Exception ex) {
                    LOGGER.error("executeStaticInitMethod {} {} error", e, clazz.getSimpleName(), declaredField.getName());
                }
            } catch (Exception e) {
                LOGGER.error("executeStaticInitMethod {} {} error", e, clazz.getSimpleName(), declaredField.getName());
            }
        }
        LOGGER.info("executeStaticInitMethod {} cost:{}", clazz.getSimpleName(), System.currentTimeMillis() - start);
    }

}
