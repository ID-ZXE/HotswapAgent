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
package org.hotswap.agent;

import org.hotswap.agent.config.PluginManager;
import org.hotswap.agent.handle.CompileEngine;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.manager.AllExtensionsManager;
import org.hotswap.agent.util.Version;
import org.hotswap.agent.watch.nio.AbstractNIO2Watcher;

import java.io.File;
import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.util.HashSet;
import java.util.Set;

/**
 * Register the agent and initialize plugin manager singleton instance.
 * <p/>
 * This class must be registered in META-INF/MANIFEST.MF:
 * Agent-Class: org.hotswap.agent.HotswapAgent
 * Premain-Class: org.hotswap.agent.HotswapAgent
 * <p/>
 * Use with -javaagent agent.jar to use with an application.
 *
 * @author Jiri Bubnik
 */
public class HotswapAgent {
    private static final AgentLogger LOGGER = AgentLogger.getLogger(HotswapAgent.class);

    /**
     * Force disable plugin, this plugin is skipped during scanning process.
     * <p/>
     * Plugin might be disabled in hotswap-agent.properties for application classloaders as well.
     */
    private static Set<String> disabledPlugins = new HashSet<>();

    /**
     * Default value for autoHotswap property.
     */
    private static boolean autoHotswap = false;

    /**
     * Path for an external properties file `hotswap-agent.properties`
     */
    private static String propertiesFilePath;

    public static void agentmain(String args, Instrumentation inst) {
        premain(args, inst);
    }

    private static final String name = "io.netty.util.internal.ObjectUtil";

    public static void premain(String args, Instrumentation inst) {
        LOGGER.debug("Loading Hotswap agent {{}} - unlimited runtime class redefinition.", Version.version());
        // 清空class文件夹
        AllExtensionsManager.getInstance().initProperties();
        CompileEngine.getInstance().cleanOldClassFile();

        parseArgs(args);
        fixJboss7Modules();
        PluginManager.getInstance().init(inst);

        watchExtraClasspath();
        AbstractNIO2Watcher abstractNIO2Watcher = (AbstractNIO2Watcher) PluginManager.getInstance().getWatcher();
        HotswapApplication.getInstance().setDispatcher(abstractNIO2Watcher.getDispatcher());
        // 初始化
        HotswapApplication.getInstance().start();
        LOGGER.debug("Hotswap agent initialized.");
    }

    public static void watchExtraClasspath() {
        File extraClasspathDir = new File(AllExtensionsManager.getInstance().getExtraClassPath());
        if (!extraClasspathDir.exists()) {
            boolean mkdirs = extraClasspathDir.mkdirs();
            if (!mkdirs) {
                throw new RuntimeException("create ext dir failure");
            }
        }
        try {
            AbstractNIO2Watcher watcher = (AbstractNIO2Watcher) PluginManager.getInstance().getWatcher();
            watcher.addDirectory(extraClasspathDir.toPath());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void parseArgs(String args) {
        if (args == null) return;

        for (String arg : args.split(",")) {
            String[] val = arg.split("=");
            if (val.length != 2) {
                LOGGER.warning("Invalid javaagent command line argument '{}'. Argument is ignored.", arg);
            }

            String option = val[0];
            String optionValue = val[1];

            if ("disablePlugin".equals(option)) {
                disabledPlugins.add(optionValue.toLowerCase());
            } else if ("autoHotswap".equals(option)) {
                autoHotswap = Boolean.valueOf(optionValue);
            } else if ("propertiesFilePath".equals(option)) {
                propertiesFilePath = optionValue;
            } else {
                LOGGER.warning("Invalid javaagent option '{}'. Argument '{}' is ignored.", option, arg);
            }
        }
    }

    /**
     * @return the path for the hotswap-agent.properties external file
     */
    public static String getExternalPropertiesFile() {
        return propertiesFilePath;
    }

    /**
     * Checks if the plugin is disabled (by name).
     *
     * @param pluginName plugin name (e.g. Tomcat, Spring, ...)
     * @return true if the plugin is disabled
     */
    public static boolean isPluginDisabled(String pluginName) {
        return disabledPlugins.contains(pluginName.toLowerCase());
    }

    /**
     * Default autoHotswap property value.
     *
     * @return true if autoHotswap=true command line option was specified
     */
    public static boolean isAutoHotswap() {
        return autoHotswap;
    }

    private static void fixJboss7Modules() {
        String JBOSS_SYSTEM_MODULES_KEY = "jboss.modules.system.pkgs";


        String oldValue = System.getProperty(JBOSS_SYSTEM_MODULES_KEY, null);
        System.setProperty(JBOSS_SYSTEM_MODULES_KEY, oldValue == null ? HOTSWAP_AGENT_EXPORT_PACKAGES : oldValue + "," + HOTSWAP_AGENT_EXPORT_PACKAGES);
    }

    public static final String HOTSWAP_AGENT_EXPORT_PACKAGES = //
            "org.hotswap.agent.annotation,"//
                    + "org.hotswap.agent.command," //
                    + "org.hotswap.agent.config," //
                    + "org.hotswap.agent.logging," + "org.hotswap.agent.plugin," //
                    + "org.hotswap.agent.util," //
                    + "org.hotswap.agent.watch," //
                    + "org.hotswap.agent.versions," //
                    + "org.hotswap.agent.javassist";
}
