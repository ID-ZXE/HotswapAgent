/*
 * Copyright 2013-2024 the HotswapAgent authors.
 *
 * This file is part of HotswapAgent.
 *
 * HotswapAgent is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation, either version 2 of the License, or (at your
 * option) any later version.
 *
 * HotswapAgent is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with HotswapAgent. If not, see http://www.gnu.org/licenses/.
 */
package org.hotswap.agent.plugin.mybatis.transformers;

import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.javassist.*;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.mybatis.MyBatisPlugin;
import org.hotswap.agent.plugin.mybatis.MyBatisPlusRefresh;
import org.hotswap.agent.util.PluginManagerInvoker;

import java.util.Objects;

/**
 * Static transformers for MyBatis plugin.
 *
 * @author Vladimir Dvorak
 */
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

        StringBuilder src = new StringBuilder("{");
        src.append(PluginManagerInvoker.buildInitializePlugin(MyBatisPlugin.class));
        src.append("}");
        noArgsConstructor.insertAfter(src.toString());
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
