package org.hotswap.agent.plugin.dubbo.transformers;

import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.javassist.*;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.dubbo.DubboPlugin;
import org.hotswap.agent.util.PluginManagerInvoker;

public class DubboTransformers {

    private static final AgentLogger LOGGER = AgentLogger.getLogger(DubboTransformers.class);

    // @OnClassLoadEvent(classNameRegexp = "org.apache.dubbo.config.spring.context.annotation.DubboClassPathBeanDefinitionScanner")
    public static void patchDubboClassPathBeanDefinitionScanner(CtClass ctClass, ClassPool classPool) throws NotFoundException, CannotCompileException {
        StringBuilder src = new StringBuilder("{");
        src.append(PluginManagerInvoker.buildInitializePlugin(DubboPlugin.class));
        // src.append("{if(true){return true;}}");
        src.append("}");

        CtMethod method = ctClass.getDeclaredMethod("checkCandidate");

        method.insertBefore(src.toString());
        LOGGER.info("patchDubboClassPathBeanDefinitionScanner success");
    }

}
