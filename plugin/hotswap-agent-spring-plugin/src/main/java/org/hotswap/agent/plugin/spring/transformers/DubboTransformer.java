package org.hotswap.agent.plugin.spring.transformers;

import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.javassist.*;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.spring.transformers.support.DubboSupport;

public class DubboTransformer {

    private static final AgentLogger LOGGER = AgentLogger.getLogger(DubboSupport.class);

    @OnClassLoadEvent(classNameRegexp = "org.apache.dubbo.config.ServiceConfig")
    public static void patchServiceConfig(CtClass clazz, ClassPool classPool) throws NotFoundException, CannotCompileException {
        CtMethod export = clazz.getDeclaredMethod("export");
        export.insertBefore("{" +
                DubboSupport.class.getName() + ".registerServiceBean($0);" +
                "}");
    }

}
