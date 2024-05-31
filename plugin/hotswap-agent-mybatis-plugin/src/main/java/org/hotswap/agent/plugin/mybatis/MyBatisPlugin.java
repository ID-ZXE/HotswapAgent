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
package org.hotswap.agent.plugin.mybatis;

import org.hotswap.agent.annotation.*;
import org.hotswap.agent.command.Command;
import org.hotswap.agent.command.ReflectionCommand;
import org.hotswap.agent.command.Scheduler;
import org.hotswap.agent.config.PluginConfiguration;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.manager.AllExtensionsManager;
import org.hotswap.agent.plugin.mybatis.transformers.MyBatisPlusTransformers;
import org.hotswap.agent.plugin.mybatis.transformers.MyBatisTransformers;
import org.hotswap.agent.util.XmlUtils;
import org.hotswap.agent.util.spring.util.StringUtils;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * Reload MyBatis configuration after entity create/change.
 *
 * @author Vladimir Dvorak
 */
@Plugin(name = "MyBatis", description = "Reload MyBatis configuration after configuration create/change.",
        testedVersions = {"All between 3.5.9"}, expectedVersions = {"3.5.9"},
        supportClass = {MyBatisTransformers.class, MyBatisPlusTransformers.class})
public class MyBatisPlugin {
    private static final AgentLogger LOGGER = AgentLogger.getLogger(MyBatisPlugin.class);

    @Init
    Scheduler scheduler;

    @Init
    ClassLoader appClassLoader;

    Map<String, Object> configurationMap = new HashMap<>();

    @Init
    public void init(PluginConfiguration pluginConfiguration) {
        LOGGER.info("MyBatis plugin initialized.");
    }

    public void registerConfigurationFile(String configFile, Object configObject) {
        if (configFile != null && !configurationMap.containsKey(configFile)) {
            LOGGER.info("MyBatisPlugin - configuration file registered : {}", configFile);
            configurationMap.put(configFile, configObject);
        }
    }

    @OnResourceFileEvent(path = "/", filter = ".*.xml", events = {FileEvent.MODIFY, FileEvent.CREATE})
    public void registerResourceListeners(URL url) throws URISyntaxException {
        String absolutePath = Paths.get(url.toURI()).toFile().getAbsolutePath();
        if (XmlUtils.isMyBatisXML(absolutePath)) {
            LOGGER.info("发现MyBatis XML Mapper变更 开始RELOAD:{}", absolutePath);
            Command command = new ReflectionCommand(this, MyBatisRefreshCommands.class.getName(), "refreshXMLMapper", appClassLoader, absolutePath);
            scheduler.scheduleCommand(command, 500);
        }
    }

    @OnClassLoadEvent(classNameRegexp = ".*", events = {LoadEvent.REDEFINE})
    public void registerClassListeners(ClassLoader classLoader, Class<?> clazz) {
        if (isMybatisMapper(clazz)) {
            LOGGER.info("发现MyBatis Annotation Mapper变更 开始RELOAD:{}", clazz.getName());
            Command command = new ReflectionCommand(this, MyBatisRefreshCommands.class.getName(), "refreshAnnotationMapper", appClassLoader, clazz);
            scheduler.scheduleCommand(command, 500);
        } else if (!clazz.isInterface() && !clazz.isEnum()
                && !clazz.isAnnotation() && !clazz.isSynthetic()) {
            Command command = new ReflectionCommand(this, MyBatisRefreshCommands.class.getName(), "refreshModelField", appClassLoader, clazz);
            scheduler.scheduleCommand(command, 500);
        }
    }

    @OnClassLoadEvent(classNameRegexp = ".*", events = {LoadEvent.REDEFINE})
    public void registerNewClassListeners(Class<?> clazz) {
        if (isMybatisMapper(clazz)) {
            LOGGER.info("发现新增MyBatis Mapper 开始LOAD:{}", clazz.getName());
            Command command = new ReflectionCommand(this, MyBatisRefreshCommands.class.getName(), "refreshNewMapperClass", appClassLoader, clazz);
            scheduler.scheduleCommand(command, 500);
        }
    }

    private static boolean isMybatisMapper(Class<?> clazz) {
        String mybatisBasePackage = AllExtensionsManager.getInstance().getMybatisBasePackage();
        if (StringUtils.isEmpty(mybatisBasePackage)) {
            return false;
        }
        String[] basePackages = mybatisBasePackage.split(",");
        for (String basePackage : basePackages) {
            if (clazz.getName().startsWith(basePackage)) {
                return true;
            }
        }
        return false;
    }

}
