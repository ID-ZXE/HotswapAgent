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
import org.apache.ibatis.reflection.DefaultReflectorFactory;
import org.apache.ibatis.reflection.Reflector;
import org.apache.ibatis.session.Configuration;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.manager.AllExtensionsManager;
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
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;


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

    private static ClassPathMapperScanner mapperScanner;

    public static void loadScanner(ClassPathMapperScanner scanner) {
        if (null != mapperScanner) {
            return;
        }
        mapperScanner = scanner;
    }

    public static void refreshModelField(Class<?> clazz) {
        try {
            Class<?> sqlSessionFactoryClz = Class.forName("org.apache.ibatis.session.defaults.DefaultSqlSessionFactory", true, AllExtensionsManager.getInstance().getClassLoader());
            Field staticConfiguration = sqlSessionFactoryClz.getDeclaredField("_staticConfiguration");
            ArrayList<Configuration> configurations = (ArrayList<Configuration>) staticConfiguration.get(null);
            if (configurations.isEmpty()) {
                LOGGER.info("configuration不存在 跳过MyBatis Model Field缓存清理");
                return;
            }

            for (Configuration configuration : configurations) {
                try {
                    try {
                        Class.forName("com.baomidou.mybatisplus.extension.activerecord.Model");
                        if (MyBatisPlusRefresh.isEntity(clazz)) {
                            MyBatisPlusRefresh.refreshModel(clazz);
                        } else {
                            cleanModelCache(configuration, clazz);
                        }
                    } catch (ClassNotFoundException e) {
                        LOGGER.error("ClassNotFound", e);
                        cleanModelCache(configuration, clazz);
                    }
                } catch (Exception e) {
                    LOGGER.error("移除MyBatis Model Field 缓存失败:{}", e, clazz.getName());
                }
            }
        } catch (Exception e) {
            LOGGER.error("refreshModelField failure", e);
        }
    }

    private static void cleanModelCache(Configuration configuration, Class<?> clazz) throws NoSuchFieldException, IllegalAccessException {
        //移除实体类对应的映射器缓存
        DefaultReflectorFactory reflectorFactory = (DefaultReflectorFactory) configuration.getReflectorFactory();
        Field reflectorMapField = DefaultReflectorFactory.class.getDeclaredField("reflectorMap");
        reflectorMapField.setAccessible(true);
        @SuppressWarnings("unchecked") ConcurrentMap<Class<?>, Reflector> reflectorMap = (ConcurrentMap<Class<?>, Reflector>) reflectorMapField.get(reflectorFactory);
        reflectorMap.remove(clazz);
        LOGGER.info("移除MyBatis Model 缓存:{}", clazz.getName());
    }

    public static void refreshNewMapperClass(Class<?> clazz) {
        if (clazz == null || !clazz.isInterface()) {
            return;
        }
        if (Objects.isNull(clazz.getAnnotation(Mapper.class)) && Objects.isNull(clazz.getAnnotation(Repository.class))) {
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
        List<Configuration> configurations = getAllConfiguration();
        if (CollectionUtils.isEmpty(configurations)) {
            LOGGER.info("没有发现Configuration 停止更新MyBatis XML Mapper");
            return;
        }

        for (Configuration configuration : configurations) {
            reloadXMLMapper(xmlPath, configuration);
        }
    }

    public static void refreshAnnotationMapper(Class<?> mapper) {
        List<Configuration> configurations = getAllConfiguration();
        if (CollectionUtils.isEmpty(configurations)) {
            LOGGER.info("没有发现Configuration 停止更新MyBatis XML Mapper");
            return;
        }

        for (Configuration configuration : configurations) {
            reloadAnnotationMapper(mapper, configuration);
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

            if (configuration.getClass().getName()
                    .equals("com.baomidou.mybatisplus.core.MybatisConfiguration")) {
                MyBatisPlusRefresh.refreshMapper(configuration, reloadMapper);
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

    private static List<Configuration> getAllConfiguration() {
        try {
            Class<?> sqlSessionFactoryClz = Class.forName("org.apache.ibatis.session.defaults.DefaultSqlSessionFactory", true, AllExtensionsManager.getInstance().getClassLoader());
            Field staticConfiguration = sqlSessionFactoryClz.getDeclaredField("_staticConfiguration");
            return (ArrayList<Configuration>) staticConfiguration.get(null);
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

}
