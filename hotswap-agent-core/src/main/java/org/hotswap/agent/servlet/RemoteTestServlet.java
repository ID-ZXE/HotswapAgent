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
            Map<String, String> contentMap = contentDTO.getContent();

            for (Map.Entry<String, String> entry : contentMap.entrySet()) {
                byte[] decode = Base64.getDecoder().decode(entry.getValue());
                File file = new File(AllExtensionsManager.getInstance().getSourceDirPath(), entry.getKey());
                FileUtils.write(file, transferTest(new String(decode)), StandardCharsets.UTF_8, false);
            }

            CompileEngine.getInstance().compile();
            HotswapApplication.getInstance().openChannel();
        } finally {
            AgentLogManager.getInstance().setRemoteTesting(false);
        }
        return null;
    }

    public String transferTest(String testCode) {
        // 解析Java文件
        JavaParser javaParser = new JavaParser();
        ParseResult<CompilationUnit> result = javaParser.parse(testCode);
        if (!result.getResult().isPresent()) {
            throw new RuntimeException("解析代码失败" + testCode);
        }
        CompilationUnit compilationUnit = result.getResult().get();

        Optional<PackageDeclaration> packageDeclarationOptional = compilationUnit.getPackageDeclaration();
        if (packageDeclarationOptional.isPresent()) {
            PackageDeclaration packageDeclaration = packageDeclarationOptional.get();
            LOGGER.info(HotswapConstants.REMOTE_TEST_TAG + "当前执行单测的package为:{},请确保与SpringBoot的Application类的basePackage一致,否则可能出现单测不执行的情况", packageDeclaration.getName());
        }

        // 遍历所有ImportDeclaration节点
        Iterator<ImportDeclaration> importIterator = compilationUnit.getImports().iterator();
        while (importIterator.hasNext()) {
            ImportDeclaration importDeclaration = importIterator.next();
            String importName = importDeclaration.getNameAsString();
            if (importName.startsWith("org.junit") || importName.startsWith("org.springframework.boot.test") || importName.startsWith("org.springframework.test")) {
                importIterator.remove();
            }
        }

        // 获取类声明
        ClassOrInterfaceDeclaration classDeclaration = (ClassOrInterfaceDeclaration) compilationUnit.getType(0);


        // 创建@Component注解
        NormalAnnotationExpr annotationExpr = new NormalAnnotationExpr();
        annotationExpr.setName("org.springframework.stereotype.Component");

        // 移除其他注解
        classDeclaration.getAnnotations().clear();

        // 添加@Component注解到类上
        classDeclaration.addAnnotation(annotationExpr);

        // 检查方法是否有@Test注解
        List<String> method = new ArrayList<>();
        for (MethodDeclaration declaration : classDeclaration.getMethods()) {
            SimpleName methodName = declaration.getName();
            declaration.setName(HotswapConstants.REMOTE_TEST_METHOD_PREFIX + declaration.getName());
            for (AnnotationExpr expr : declaration.getAnnotations()) {
                if (expr.getNameAsString().equals("Test")) {
                    if (!declaration.getParameters().isEmpty()) {
                        LOGGER.info(HotswapConstants.REMOTE_TEST_TAG + "Test方法:{}存在入参, 不执行", methodName);
                        continue;
                    }
                    method.add(declaration.getName().toString());
                }
            }
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
            for (String s : method) {
                body.append("try {");
                body.append(s).append("()").append(";");
                body.append("} catch(Exception e) {");
                body.append("e.printStackTrace();");
                body.append("}");
            }
            body.append("}");

            BlockStmt blockStmt = StaticJavaParser.parseBlock(body.toString());
            indexMethod.setBody(blockStmt);
            indexMethod.addAnnotation("javax.annotation.PostConstruct");
        }

        // 保存修改
        return new DefaultPrettyPrinter().print(result.getResult().get());

    }

}
