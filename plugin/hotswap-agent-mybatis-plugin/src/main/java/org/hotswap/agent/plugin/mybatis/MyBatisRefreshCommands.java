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

import org.apache.commons.io.FileUtils;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.binding.MapperProxyFactory;
import org.apache.ibatis.binding.MapperRegistry;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.builder.xml.XMLMapperEntityResolver;
import org.apache.ibatis.parsing.XNode;
import org.apache.ibatis.parsing.XPathParser;
import org.apache.ibatis.session.Configuration;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.mybatis.proxy.ConfigurationProxy;
import org.hotswap.agent.util.ReflectionHelper;
import org.hotswap.agent.util.spring.util.CollectionUtils;
import org.hotswap.agent.util.spring.util.ReflectionUtils;
import org.mybatis.spring.mapper.ClassPathMapperScanner;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanNameGenerator;
import org.springframework.stereotype.Repository;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;


/**
 * Reload the MyBatis configuration.
 * <p/>
 * This class must run in App classloader.
 *
 * @author Vladimir Dvorak
 */
public class MyBatisRefreshCommands {
    private static final AgentLogger LOGGER = AgentLogger.getLogger(MyBatisRefreshCommands.class);

    /**
     * Flag to check reload status.
     * In unit test we need to wait for reload finish before the test can continue. Set flag to true
     * in the test class and wait until the flag is false again.
     */
    public static boolean reloadFlag = false;

//    public static void reloadConfiguration() {
//        LOGGER.debug("Refreshing MyBatis configuration.");
//        ConfigurationProxy.refreshProxiedConfigurations();
//        SpringMybatisConfigurationProxy.refreshProxiedConfigurations();
//        LOGGER.reload("MyBatis configuration refreshed.");
//        reloadFlag = false;
//    }

    private static ClassPathMapperScanner mapperScanner;

    public static void loadScanner(ClassPathMapperScanner scanner) {
        if (null != mapperScanner) {
            return;
        }
        mapperScanner = scanner;
    }

    public static void refreshNewMapperClass(Class<?> clazz) {
        if (clazz == null || !clazz.isInterface()) {
            return;
        }
        if (Objects.isNull(clazz.getAnnotation(Mapper.class))
                && Objects.isNull(clazz.getAnnotation(Repository.class))) {
            return;
        }
        if (null == mapperScanner) {
            return;
        }
        try {
            BeanDefinitionBuilder beanDefinitionBuilder = BeanDefinitionBuilder.genericBeanDefinition(clazz);
            BeanDefinition beanDefinition = beanDefinitionBuilder.getRawBeanDefinition();
            beanDefinition.setScope(BeanDefinition.SCOPE_SINGLETON);

            BeanNameGenerator beanNameGenerator = (BeanNameGenerator) ReflectionHelper.get(mapperScanner, "beanNameGenerator");
            BeanDefinitionRegistry registry = (BeanDefinitionRegistry) ReflectionHelper.get(mapperScanner, "registry");
            String beanName = beanNameGenerator.generateBeanName(beanDefinition, registry);

            BeanDefinitionHolder definitionHolder = new BeanDefinitionHolder(beanDefinition, beanName);
            registerBeanDefinition(definitionHolder);
        } catch (Exception e) {
            LOGGER.error("Refresh Mybatis Bean err", e);
        }
    }

    private static void registerBeanDefinition(BeanDefinitionHolder holder) {
        if (null == mapperScanner) {
            return;
        }
        try {
            Set<BeanDefinitionHolder> holders = new HashSet<>();
            holders.add(holder);
            Method method = Class.forName("org.mybatis.spring.mapper.ClassPathMapperScanner").getDeclaredMethod("processBeanDefinitions", Set.class);
            boolean isAccess = method.isAccessible();
            method.setAccessible(true);
            method.invoke(mapperScanner, holders);
            method.setAccessible(isAccess);
        } catch (Exception e) {
            LOGGER.error("freshMyBatis err", e);
        }
    }

    public static void refreshXMLMapper(String xmlPath) {
        Collection<ConfigurationProxy> allConfigurationProxy = ConfigurationProxy.getAllConfigurationProxy();
        if (CollectionUtils.isEmpty(allConfigurationProxy)) {
            LOGGER.info("没有发现Configuration 停止更新MyBatis XML Mapper");
            return;
        }

        for (ConfigurationProxy configurationProxy : allConfigurationProxy) {
            reloadXMLMapper(xmlPath, configurationProxy.getConfiguration());
        }
    }

    public static void refreshAnnotationMapper(Class<?> mapper) {
        Collection<ConfigurationProxy> allConfigurationProxy = ConfigurationProxy.getAllConfigurationProxy();
        if (CollectionUtils.isEmpty(allConfigurationProxy)) {
            LOGGER.info("没有发现Configuration 停止更新MyBatis XML Mapper");
            return;
        }

        for (ConfigurationProxy configurationProxy : allConfigurationProxy) {
            reloadAnnotationMapper(mapper, configurationProxy.getConfiguration());
        }
    }

    private static void reloadXMLMapper(String mapperFilePath, Configuration configuration) {
        Path path = Paths.get(mapperFilePath);
        if (!Files.exists(path)) {
            LOGGER.info("mybatis reload mapper xml not exist {}", mapperFilePath);
            return;
        }

        File file = path.toFile();
        String xml;
        try {
            xml = FileUtils.readFileToString(file, "UTF-8");
        } catch (Exception e) {
            LOGGER.error("read xml file error", e);
            return;
        }

        loadNewMapperFile(xml, configuration);
    }

    private static void loadNewMapperFile(String xml, Configuration configuration) {
        try {
            XPathParser context = new XPathParser(new ByteArrayInputStream(xml.getBytes()), true, configuration.getVariables(), new XMLMapperEntityResolver());
            XNode contextNode = context.evalNode("/mapper");
            if (null == contextNode) {
                return;
            }
            String namespace = contextNode.getStringAttribute("namespace");
            if (namespace == null || namespace.isEmpty()) {
                LOGGER.error("Mapper's namespace cannot be empty");
                return;
            }


            Set<?> loadedResources = ReflectionUtils.getField("loadedResources", configuration);
            if (CollectionUtils.isEmpty(loadedResources)) {
                LOGGER.info("loadedResources is empty");
                return;
            }
            if (!loadedResources.contains(generateNamespace(namespace)) && !loadedResources.contains(generateInterfaceNamespace(namespace))) {
                LOGGER.info("不是当前sqlSessionFactory的mapper文件:{}", namespace);
                return;
            }

            loadedResources.remove(generateNamespace(namespace));
            String xmlResource = generateNamespace(namespace);
            XMLMapperBuilder xmlMapperBuilder = new XMLMapperBuilder(new ByteArrayInputStream(xml.getBytes()), configuration, xmlResource, configuration.getSqlFragments());
            xmlMapperBuilder.parse();
        } catch (Exception e) {
            LOGGER.error("loadNewMapperFile error", e);
        }
    }

    private static void reloadAnnotationMapper(Class<?> reloadMapper, Configuration configuration) {
        try {
            MapperRegistry mapperRegistry = ReflectionUtils.getField("mapperRegistry", configuration);
            Map<Class<?>, MapperProxyFactory<?>> knownMappers = ReflectionUtils.getField("knownMappers", mapperRegistry);
            // 删除已经注册的Mapper
            knownMappers.remove(reloadMapper);

            // 存储的key分为1.interface mapper 2.namespace:mapper 3. file [mapper.xml]
            Set<String> loadedResources = ReflectionUtils.getField("loadedResources", configuration);
            if (CollectionUtils.isEmpty(loadedResources) || !loadedResources.contains(generateInterfaceNamespace(reloadMapper.getName()))) {
                LOGGER.info("not cur sql session factory:{}", reloadMapper.getName());
                return;
            }
            loadedResources.remove(generateInterfaceNamespace(reloadMapper.getName()));

            // 重新加载按注解方式实现的 mybatis
            mapperRegistry.addMapper(reloadMapper);
            LOGGER.info("reload annotation mapper:{}", reloadMapper.getName());
        } catch (Exception ex) {
            LOGGER.info("reloadAnnotationMapper error:{}", reloadMapper.getName(), ex);
        }
    }

    private static String generateNamespace(String namespace) {
        return "namespace:" + namespace;
    }

    private static String generateInterfaceNamespace(String interfaceName) {
        return "interface " + interfaceName;
    }

}
