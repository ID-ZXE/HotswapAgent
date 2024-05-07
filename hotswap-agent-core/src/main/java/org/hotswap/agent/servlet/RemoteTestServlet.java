package org.hotswap.agent.servlet;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.SimpleName;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.printer.DefaultPrettyPrinter;
import org.apache.commons.io.FileUtils;
import org.hotswap.agent.HotswapApplication;
import org.hotswap.agent.constants.HotswapConstants;
import org.hotswap.agent.dto.ContentDTO;
import org.hotswap.agent.handle.CompileEngine;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.manager.AgentLogManager;
import org.hotswap.agent.manager.AllExtensionsManager;
import org.hotswap.agent.util.JsonUtils;
import org.hotswap.agent.util.spring.util.CollectionUtils;
import org.hotswap.agent.util.spring.util.StringUtils;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class RemoteTestServlet extends AbstractHttpServlet {

    private static final AgentLogger LOGGER = AgentLogger.getLogger(RemoteTestServlet.class);

    @Override
    public Object doExecute() throws Exception {
        AgentLogManager.getInstance().setRemoteTesting(true);
        try {
            ContentDTO contentDTO = JsonUtils.toObject(body, ContentDTO.class);
            Map<String, String> codeMap = contentDTO.getContent();

            String methodName = "";
            if (!CollectionUtils.isEmpty(contentDTO.getExtraData())) {
                methodName = contentDTO.getExtraData().get("remoteTestMethodName");
            }

            for (Map.Entry<String, String> entry : codeMap.entrySet()) {
                byte[] decode = Base64.getDecoder().decode(entry.getValue());
                File file = new File(AllExtensionsManager.getInstance().getSourceDirPath(), entry.getKey());
                String code = transferTest(new String(decode), methodName);

                // 未找到需要执行的测试用例
                if (StringUtils.isEmpty(code)) {
                    return null;
                }

                LOGGER.info("rebuild remote test java class\n {}", code);
                FileUtils.write(file, code, StandardCharsets.UTF_8, false);
            }

            try {
                CompileEngine.getInstance().compile();
            } catch (Exception e) {
                LOGGER.warning(HotswapConstants.REMOTE_TEST_TAG + "编译失败", e);
                throw e;
            }

            HotswapApplication.getInstance().openChannel();
        } finally {
            AgentLogManager.getInstance().setRemoteTesting(false);
        }
        return null;
    }

    public String transferTest(String testCode, String needRunMethodName) {
        // 解析Java文件
        JavaParser javaParser = new JavaParser();
        ParseResult<CompilationUnit> result = javaParser.parse(testCode);
        if (!result.getResult().isPresent()) {
            throw new RuntimeException("解析代码失败" + testCode);
        }
        CompilationUnit compilationUnit = result.getResult().get();

        // 获取类声明
        ClassOrInterfaceDeclaration classDeclaration = (ClassOrInterfaceDeclaration) compilationUnit.getType(0);
        if (!classDeclaration.getExtendedTypes().isEmpty()) {
            LOGGER.error(HotswapConstants.REMOTE_TEST_TAG + "远程单测无法执行");
            LOGGER.error(HotswapConstants.REMOTE_TEST_TAG + "请删除继承结构");
            return "";
        }
        if (!classDeclaration.getImplementedTypes().isEmpty()) {
            LOGGER.error(HotswapConstants.REMOTE_TEST_TAG + "远程单测无法执行");
            LOGGER.error(HotswapConstants.REMOTE_TEST_TAG + "请删除接口结构");
            return "";
        }

        Optional<PackageDeclaration> packageDeclarationOptional = compilationUnit.getPackageDeclaration();
        if (packageDeclarationOptional.isPresent()) {
            PackageDeclaration packageDeclaration = packageDeclarationOptional.get();
            // 改写package
            packageDeclaration.setName(AllExtensionsManager.getInstance().getSpringbootBasePackage());
        }
        LOGGER.warning(HotswapConstants.REMOTE_TEST_TAG + "单测执行时间过长会导致IDEA插件端接口超时，若超时请前往服务器自行查看日志");
        LOGGER.warning(HotswapConstants.REMOTE_TEST_TAG + "超时时间为2min");
        LOGGER.warning(HotswapConstants.REMOTE_TEST_TAG + "请自行设置TraceID");

        // 遍历所有ImportDeclaration节点
        Iterator<ImportDeclaration> importIterator = compilationUnit.getImports().iterator();
        while (importIterator.hasNext()) {
            ImportDeclaration importDeclaration = importIterator.next();
            String importName = importDeclaration.getNameAsString();
            if (importName.startsWith("org.junit")
                    || importName.startsWith("org.springframework.boot.test")
                    || importName.startsWith("org.springframework.test")) {
                importIterator.remove();
            }
        }

        // 创建@Component注解
        NormalAnnotationExpr component = new NormalAnnotationExpr();
        component.setName("org.springframework.stereotype.Component");

        NormalAnnotationExpr slf4j = new NormalAnnotationExpr();
        slf4j.setName("lombok.extern.slf4j.Slf4j");

        // 移除其他注解
        classDeclaration.getAnnotations().clear();

        // 添加@Component注解到类上
        classDeclaration.addAnnotation(component);
        classDeclaration.addAnnotation(slf4j);

        // 检查方法是否有@Test注解
        List<String> method = new ArrayList<>();
        for (MethodDeclaration declaration : classDeclaration.getMethods()) {
            SimpleName methodName = declaration.getName();
            declaration.setName(HotswapConstants.REMOTE_TEST_METHOD_PREFIX + declaration.getName());
            for (AnnotationExpr expr : declaration.getAnnotations()) {
                if (expr.getNameAsString().equals("Test")) {
                    if (!declaration.getParameters().isEmpty()) {
                        LOGGER.warning(HotswapConstants.REMOTE_TEST_TAG + "Test方法:{}存在入参, 不执行", methodName);
                        continue;
                    }

                    if (!StringUtils.isEmpty(needRunMethodName)) {
                        if (Objects.equals(needRunMethodName, methodName.asString())) {
                            method.add(declaration.getName().toString());
                        }
                    } else {
                        method.add(declaration.getName().toString());
                    }
                }
            }
        }

        if (CollectionUtils.isEmpty(method)) {
            LOGGER.error("未找到需要执行的远程单元测试！！！");
            return "";
        }

        // 删除Test注解
        for (MethodDeclaration declaration : classDeclaration.getMethods()) {
            declaration.getAnnotations().clear();
        }

        // 新增init方法
        if (!method.isEmpty()) {
            MethodDeclaration indexMethod = classDeclaration.addMethod(HotswapConstants.RUN_REMOTE_METHOD_NAME, Modifier.Keyword.PUBLIC);
            indexMethod.setType(StaticJavaParser.parseType("void"));
            StringBuilder body = new StringBuilder("{");
            body.append("Thread thread = new Thread(new Runnable() {\n" +
                    "                @Override\n" +
                    "                public void run() {");
            for (String s : method) {
                body.append("try {");
                body.append(s).append("()").append(";");
                body.append("} catch(Exception e) {");
                body.append("e.printStackTrace();");
                body.append("}");
            }
            body.append("}});\n" +
                    "            thread.start();" +
                    "            thread.join(2 * 60 * 1000);");
            body.append("}");

            BlockStmt blockStmt = StaticJavaParser.parseBlock(body.toString());
            indexMethod.setBody(blockStmt);
            indexMethod.addAnnotation("javax.annotation.PostConstruct");
        }

        // 保存修改
        return new DefaultPrettyPrinter().print(result.getResult().get());

    }

}
