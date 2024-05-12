package org.hotswap.agent.plugin.mybatis.transformers;

import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.javassist.*;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.mybatis.MyBatisPlugin;
import org.hotswap.agent.util.PluginManagerInvoker;

import java.util.Objects;

public class MyBatisPlusTransformers {

    private static final AgentLogger LOGGER = AgentLogger.getLogger(MyBatisPlusTransformers.class);

    @OnClassLoadEvent(classNameRegexp = "com.baomidou.mybatisplus.core.MybatisConfiguration")
    public static void patchPlusConfiguration(CtClass ctClass, ClassPool classPool) throws NotFoundException, CannotCompileException {
        CtMethod removeMappedStatementMethod = CtNewMethod.make(
                "public void $$removeMappedStatement(String statementName)" +
                        "{if(mappedStatements.containsKey(statementName))" +
                        "{mappedStatements.remove(statementName);}}", ctClass);
        ctClass.addMethod(removeMappedStatementMethod);
        ctClass.getDeclaredMethod("addMappedStatement",
                        new CtClass[]{classPool.get("org.apache.ibatis.mapping.MappedStatement")})
                .insertBefore("$$removeMappedStatement($1.getId());");

        CtConstructor[] constructors = ctClass.getConstructors();
        CtConstructor noArgsConstructor = null;
        for (CtConstructor constructor : constructors) {
            if (constructor.getParameterTypes().length == 0) {
                noArgsConstructor = constructor;
            }
        }
        if (Objects.isNull(noArgsConstructor)) {
            LOGGER.info("not found MybatisConfiguration noArgsConstructor");
            return;
        }

        String src = "{" +
                PluginManagerInvoker.buildInitializePlugin(MyBatisPlugin.class) +
                "}";
        noArgsConstructor.insertAfter(src);
        LOGGER.info("patch MybatisPlus Configuration success");
    }

    @OnClassLoadEvent(classNameRegexp = "com.baomidou.mybatisplus.core.MybatisConfiguration\\$StrictMap")
    public static void patchPlusStrictMap(CtClass ctClass, ClassPool classPool) throws NotFoundException, CannotCompileException {
        CtMethod method = ctClass.getDeclaredMethod("put",
                new CtClass[]{classPool.get(String.class.getName()),
                        classPool.get(Object.class.getName())});
        method.insertBefore("if(containsKey($1)){remove($1);}");
        LOGGER.info("patch MybatisConfiguration$StrictMap success");
    }

}
