package org.hotswap.agent.plugin.spring.transformers.support;

import org.apache.dubbo.config.ServiceConfig;
import org.hotswap.agent.logging.AgentLogger;

import java.util.HashMap;
import java.util.Map;

public class DubboSupport {

    private static final AgentLogger LOGGER = AgentLogger.getLogger(DubboSupport.class);

    @SuppressWarnings("all")
    private static final Map<String, ServiceConfig> serviceConfigMap = new HashMap<>();

    public static void registerServiceBean(ServiceConfig serviceConfig) {
        LOGGER.info("registerServiceBean:{}", serviceConfig.getId());
        serviceConfigMap.put(serviceConfig.getId(), serviceConfig);
    }

}
