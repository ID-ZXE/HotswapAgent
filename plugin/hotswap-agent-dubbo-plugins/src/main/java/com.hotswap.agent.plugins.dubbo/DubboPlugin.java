package com.hotswap.agent.plugins.dubbo;


import org.hotswap.agent.annotation.Init;
import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.annotation.Plugin;
import org.hotswap.agent.command.Scheduler;
import org.hotswap.agent.config.PluginConfiguration;
import org.hotswap.agent.javassist.*;
import org.hotswap.agent.logging.AgentLogger;

import java.util.HashMap;
import java.util.Map;

/**
 * Reload Dubbo
 */
@Plugin(name = "Dubbo", description = "Reload MyBatis configuration after configuration create/change.", testedVersions = {"All between 3.5.9"}, expectedVersions = {"3.5.9"})
public class DubboPlugin {
    private static final AgentLogger LOGGER = AgentLogger.getLogger(DubboPlugin.class);

    @Init
    Scheduler scheduler;

    @Init
    ClassLoader appClassLoader;

    Map<String, Object> configurationMap = new HashMap<>();

    @Init
    public void init(PluginConfiguration pluginConfiguration) {
        LOGGER.info("Dubbo plugin initialized.");
    }

    @OnClassLoadEvent(classNameRegexp = "org.apache.dubbo.config.spring.context.annotation.DubboClassPathBeanDefinitionScanner")
    public static void patchDubboClassPathBeanDefinitionScanner(CtClass ctClass, ClassPool classPool) throws NotFoundException, CannotCompileException {
        CtMethod method = ctClass.getDeclaredMethod("checkCandidate");
        method.insertBefore("{if(true){return true;}}");
    }


}
