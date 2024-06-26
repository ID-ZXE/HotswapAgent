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
package org.hotswap.agent.plugin.spring.boot;

import java.util.concurrent.atomic.AtomicBoolean;

import org.hotswap.agent.annotation.Init;
import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.annotation.Plugin;
import org.hotswap.agent.command.Scheduler;
import org.hotswap.agent.manager.AllExtensionsManager;
import org.hotswap.agent.javassist.CannotCompileException;
import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.CtConstructor;
import org.hotswap.agent.javassist.CtMethod;
import org.hotswap.agent.javassist.NotFoundException;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.spring.boot.transformers.PropertySourceLoaderTransformer;
import org.hotswap.agent.plugin.spring.boot.transformers.PropertySourceTransformer;
import org.hotswap.agent.util.PluginManagerInvoker;


@Plugin(name = "SpringBoot", description = "Reload Spring Boot after properties/yaml changed.", testedVersions = {"All between 1.5.x - 2.7.x"}, expectedVersions = {"1.5.x+", "2.x"}, supportClass = {PropertySourceLoaderTransformer.class, PropertySourceTransformer.class})
public class SpringBootPlugin {

    private static final AgentLogger LOGGER = AgentLogger.getLogger(SpringBootPlugin.class);

    @Init
    Scheduler scheduler;

    @Init
    ClassLoader appClassLoader;

    private final AtomicBoolean isInit = new AtomicBoolean(false);


    public void init() {
        if (isInit.compareAndSet(false, true)) {
            LOGGER.info("Spring Boot plugin initialized");
        }
    }

    public void init(String version) throws ClassNotFoundException {
        if (isInit.compareAndSet(false, true)) {
            LOGGER.info("Spring Boot plugin initialized - Spring Boot core version '{}'", version);
        }
    }

    @OnClassLoadEvent(classNameRegexp = "org.springframework.boot.SpringApplication")
    public static void register(ClassLoader appClassLoader, CtClass clazz, ClassPool classPool) throws CannotCompileException, NotFoundException {
        StringBuilder src = new StringBuilder("{");
        // init a spring plugin with every app classloader
        src.append(PluginManagerInvoker.buildInitializePlugin(SpringBootPlugin.class));
        src.append(PluginManagerInvoker.buildCallPluginMethod(SpringBootPlugin.class, "init", "org.springframework.boot.SpringBootVersion.getVersion()", String.class.getName()));
        src.append("}");

        for (CtConstructor constructor : clazz.getDeclaredConstructors()) {
            constructor.insertBeforeBody(src.toString());
        }

        CtMethod method = clazz.getDeclaredMethod("createApplicationContext");
        method.insertAfter("{org.hotswap.agent.plugin.spring.boot.listener.PropertySourceChangeListener.register($_);}");

        // 注册SpringBoot的Classloader
        ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        LOGGER.info("registrySpringBootClassLoader contextClassLoader:{}", contextClassLoader);
        AllExtensionsManager.getInstance().setClassLoader(contextClassLoader);

        CtMethod runMethod = clazz.getDeclaredMethod("run", new CtClass[]{classPool.get("java.lang.Class"),
                classPool.get("java.lang.String[]")});
        runMethod.insertBefore("{" + AllExtensionsManager.class.getName() +
                ".getInstance().setSpringbootBasePackage($1.getName());" +
                "}");

        runMethod.insertAfter("{" +
                AllExtensionsManager.class.getName() + ".getInstance().setServerIsRunning(true);" +
                "}");
    }

    @OnClassLoadEvent(classNameRegexp = "org.springframework.core.env.AbstractEnvironment")
    public static void cglibAopProxyDisableCache(CtClass ctClass) throws NotFoundException, CannotCompileException {
        CtMethod method = ctClass.getDeclaredMethod("setActiveProfiles");
        method.insertBefore("{" + AllExtensionsManager.class.getName() + ".getInstance().setProfile($1);}");

        LOGGER.debug("patch org.springframework.core.env.AbstractEnvironment");
    }

//    @OnClassLoadEvent(classNameRegexp = "org.springframework.web.servlet.view.ResourceBundleViewResolver")
//    public static void patchDisposableBean(CtClass ctClass) throws NotFoundException, CannotCompileException {
//        CtMethod destroy = ctClass.getDeclaredMethod("destroy");
//        destroy.insertBefore("{" +
//                AllExtensionsManager.class.getName() + ".getInstance().setServerIsRunning(false);" +
//                "}");
//        LOGGER.debug("patch org.springframework.web.servlet.view.ResourceBundleViewResolver");
//    }
//
//    @OnClassLoadEvent(classNameRegexp = "org.apache.dubbo.spring.boot.context.event.AwaitingNonWebApplicationListener")
//    public static void patchAwaitingNonWebApplicationListener(CtClass ctClass) throws NotFoundException, CannotCompileException {
//        CtMethod release = ctClass.getDeclaredMethod("release");
//        release.insertBefore("{" +
//                AllExtensionsManager.class.getName() + ".getInstance().setServerIsRunning(false);" +
//                "}");
//        LOGGER.debug("patch org.apache.dubbo.spring.boot.context.event.AwaitingNonWebApplicationListener");
//    }

    @OnClassLoadEvent(classNameRegexp = "org.springframework.boot.SpringApplicationShutdownHook")
    public static void patchSpringApplicationShutdownHook(CtClass ctClass) throws NotFoundException, CannotCompileException {
        CtMethod run = ctClass.getDeclaredMethod("run");
        run.insertBefore("{" +
                AllExtensionsManager.class.getName() + ".getInstance().setServerIsRunning(false);" +
                "}");
        LOGGER.debug("patch org.apache.dubbo.spring.boot.context.event.AwaitingNonWebApplicationListener");
    }

}
