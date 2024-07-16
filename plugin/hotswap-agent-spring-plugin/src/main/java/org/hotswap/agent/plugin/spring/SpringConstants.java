package org.hotswap.agent.plugin.spring;

import java.util.HashSet;

public class SpringConstants {

    public static final HashSet<String> IGNORE_INTERFACE = new HashSet<String>() {{
        add("org.springframework.aop.SpringProxy");
        add("org.springframework.aop.framework.Advised");
        add("org.springframework.cglib.proxy.Factory");
        add("org.hotswap.agent.plugin.spring.getbean.SpringHotswapAgentProxy");
        add("java.io.Serializable");
    }};

    public static boolean ignore(String interfaceName) {
        return IGNORE_INTERFACE.contains(interfaceName) || interfaceName.startsWith("java");
    }


}
