/*
 * Copyright 2013-2023 the HotswapAgent authors.
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
package org.hotswap.agent.plugin.spring.processor;

import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.spring.utils.RegistryUtils;
import org.hotswap.agent.util.ReflectionHelper;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.annotation.ConfigurationClassPostProcessor;
import org.springframework.core.Conventions;
import org.springframework.core.type.classreading.MetadataReaderFactory;

import java.lang.reflect.Method;
import java.util.Map;

public class ConfigurationClassPostProcessorAgent {
    private static final AgentLogger LOGGER = AgentLogger.getLogger(ConfigurationClassPostProcessorAgent.class);

    private static final ConfigurationClassPostProcessorAgent INSTANCE = new ConfigurationClassPostProcessorAgent();

    private static final String CONFIGURATION_CLASS_ATTRIBUTE =
            Conventions.getQualifiedAttributeName(ConfigurationClassPostProcessor.class, "configurationClass");

    private ConfigurationClassPostProcessor processor;


    public static ConfigurationClassPostProcessorAgent getInstance() {
        return INSTANCE;
    }

    private ConfigurationClassPostProcessorAgent() {
    }

    public void setProcessor(ConfigurationClassPostProcessor processor) {
        LOGGER.debug("ConfigurationClassPostProcessorAgent.setProcessor({})", processor);
        this.processor = processor;
    }

    public ConfigurationClassPostProcessor getProcessor() {
        return processor;
    }

    public void postProcess(BeanDefinitionRegistry registry, String beanName) {
        if (processor == null) {
            return;
        }

        resetCachingMetadataReaderFactoryCache();
        resetBeanNameCache();
        resetBeanFactoryCache(registry);
        removeBeanAttribute(registry, beanName);

        processor.processConfigBeanDefinitions(registry);
    }

    private MetadataReaderFactory getMetadataReaderFactory() {
        return (MetadataReaderFactory) ReflectionHelper.get(processor, ConfigurationClassPostProcessor.class,
                "metadataReaderFactory");
    }

    private void resetCachingMetadataReaderFactoryCache() {
        MetadataReaderFactory metadataReaderFactory = getMetadataReaderFactory();
        if (metadataReaderFactory != null) {
            try {
                ReflectionHelper.invoke(metadataReaderFactory, "clearCache");
            } catch (Exception e) {
                LOGGER.debug("Unable to clear MetadataReaderFactory cache");
            }
        }
    }

    private void resetBeanFactoryCache(BeanDefinitionRegistry registry) {
        DefaultListableBeanFactory beanFactory = RegistryUtils.maybeRegistryToBeanFactory(registry);
        if (beanFactory == null) {
            return;
        }

        beanFactory.setAllowBeanDefinitionOverriding(true);
        resetFactoryMethodCandidateCache(beanFactory);
    }

    private void removeBeanAttribute(BeanDefinitionRegistry registry, String beanName) {
        BeanDefinition bd = registry.getBeanDefinition(beanName);
        if (bd.hasAttribute(CONFIGURATION_CLASS_ATTRIBUTE)) {
            bd.removeAttribute(CONFIGURATION_CLASS_ATTRIBUTE);
        }
    }

    private void resetFactoryMethodCandidateCache(DefaultListableBeanFactory factory) {
        Map<Class<?>, Method[]> cache = (Map<Class<?>, Method[]>) ReflectionHelper.get(factory,
                AbstractAutowireCapableBeanFactory.class, "factoryMethodCandidateCache");
        if (cache != null) {
            LOGGER.debug("Cache cleared: AbstractAutowireCapableBeanFactory.factoryMethodCandidateCache");
            cache.clear();
        }
    }

    private void resetBeanNameCache() {
        Map<Method, String> cache = (Map<Method, String>) ReflectionHelper.getNoException(null,
                "org.springframework.context.annotation.BeanAnnotationHelper",
                processor.getClass().getClassLoader(), "beanNameCache");
        if (cache != null) {
            LOGGER.debug("Cache cleared: BeanAnnotationHelper.beanNameCache");
            cache.clear();
        }
    }
}