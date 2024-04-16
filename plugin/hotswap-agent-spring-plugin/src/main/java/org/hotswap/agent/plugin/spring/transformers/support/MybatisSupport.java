package org.hotswap.agent.plugin.spring.transformers.support;

import org.hotswap.agent.logging.AgentLogger;

import java.util.HashSet;
import java.util.Set;

public class MybatisSupport {

    private static final AgentLogger LOGGER = AgentLogger.getLogger(DubboSupport.class);

    public static final Set<String> CONFLICT_MAPPER_BEANS = new HashSet<>();

}
