package org.hotswap.agent.plugin.spring.transformers.support;

import org.apache.dubbo.config.ServiceConfig;
import org.apache.dubbo.config.spring.ServiceBean;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.manager.AllExtensionsManager;
import org.hotswap.agent.util.ReflectionHelper;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@SuppressWarnings("all")
public class DubboSupport {

    private static final AgentLogger LOGGER = AgentLogger.getLogger(DubboSupport.class);

    public static final Set<String> CONFLICT_DUBBO_BEANS = new HashSet<>();

    private static final Map<String, ServiceConfig> serviceConfigMap = new HashMap<>();

    public static void registerServiceBean(ServiceConfig serviceConfig) {
        LOGGER.info("registerServiceBean:{}", serviceConfig.getId());
        serviceConfigMap.put(serviceConfig.getId(), serviceConfig);
    }

    public static ServiceConfig getServiceBean(String interfaceName) {
        return serviceConfigMap.get(interfaceName);
    }

    public static void doInvokeApacheDubbo(ServiceBean serviceBean, Object bean) throws Exception {
        ReflectionHelper.invoke(serviceBean, serviceBean.getClass(), "setRef", new Class[]{Object.class}, bean);
        ReflectionHelper.invoke(serviceBean, "unexport");
        ReflectionHelper.set(serviceBean, AllExtensionsManager.getInstance().
                getClassLoader().loadClass("org.apache.dubbo.config.ServiceConfig"), "unexported", false);
        ReflectionHelper.set(serviceBean, AllExtensionsManager.getInstance().
                getClassLoader().loadClass("org.apache.dubbo.config.ServiceConfig"), "exported", false);
        Thread.sleep(2000);
        ReflectionHelper.invoke(serviceBean, "export");
    }

}
