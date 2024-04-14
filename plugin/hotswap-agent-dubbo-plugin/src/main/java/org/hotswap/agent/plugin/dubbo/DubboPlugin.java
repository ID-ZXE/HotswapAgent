package org.hotswap.agent.plugin.dubbo;


import org.hotswap.agent.annotation.Init;
import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.annotation.Plugin;
import org.hotswap.agent.command.Scheduler;
import org.hotswap.agent.config.PluginConfiguration;
import org.hotswap.agent.javassist.*;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.dubbo.transformers.DubboTransformers;

/**
 * Reload Dubbo
 */
@Plugin(name = "Dubbo", description = "Reload Dubbo",
        testedVersions = {"2.7.6"},
        expectedVersions = {"2.7.6"},
        supportClass = {DubboTransformers.class})
public class DubboPlugin {

    private static final AgentLogger LOGGER = AgentLogger.getLogger(DubboPlugin.class);

    @Init
    Scheduler scheduler;

    @Init
    ClassLoader appClassLoader;

    @Init
    public void init(PluginConfiguration pluginConfiguration) {
        LOGGER.info("Dubbo plugin initialized.");
    }

}
