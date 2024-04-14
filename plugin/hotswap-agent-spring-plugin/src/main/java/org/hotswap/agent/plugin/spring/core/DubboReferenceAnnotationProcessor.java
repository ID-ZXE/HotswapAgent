package org.hotswap.agent.plugin.spring.core;


import org.apache.dubbo.config.spring.beans.factory.annotation.ReferenceAnnotationBeanPostProcessor;
import org.hotswap.agent.logging.AgentLogger;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class DubboReferenceAnnotationProcessor {

    private static final AgentLogger LOGGER = AgentLogger.getLogger(DubboReferenceAnnotationProcessor.class);

    public static DefaultListableBeanFactory beanFactory;

    public static void reset(DefaultListableBeanFactory beanFactory, Set<String> beansToProcess, Set<String> newBeanNames) {
        Set<String> allBeans = new HashSet<>();
        allBeans.addAll(beansToProcess);
        allBeans.addAll(newBeanNames);

        try {
            Map<String, ReferenceAnnotationBeanPostProcessor> postProcessors = beanFactory.getBeansOfType(ReferenceAnnotationBeanPostProcessor.class);
            if (postProcessors == null || postProcessors.isEmpty()) {
                LOGGER.debug("ReferenceAnnotationBeanPostProcessor not exist");
                return;
            }

            ReferenceAnnotationBeanPostProcessor postProcessor = postProcessors.values().iterator().next();
            resetAnnotationBeanPostProcessorCache(postProcessor);
            for (String beanName : allBeans) {
                Object object = beanFactory.getSingleton(beanName);
                if (object != null) {
                    postProcessor.postProcessPropertyValues(null, null, object, beanName);
                }
            }
        } catch (Exception e) {
            LOGGER.info("ReferenceAnnotationBeanPostProcessor maybe not exist", e);
        }
    }

    private static void resetAnnotationBeanPostProcessorCache(Object object) {
        try {
            Class<?> superclass = object.getClass().getSuperclass();

            Field field = superclass.getDeclaredField("injectionMetadataCache");
            field.setAccessible(true);
            Map injectionMetadataCache = (Map) field.get(object);
            injectionMetadataCache.clear();
            LOGGER.trace("Cache cleared: AutowiredAnnotationBeanPostProcessor/CommonAnnotationBeanPostProcessor" + " injectionMetadataCache");
        } catch (Exception e) {
            throw new IllegalStateException("Unable to clear " + "AutowiredAnnotationBeanPostProcessor/CommonAnnotationBeanPostProcessor injectionMetadataCache", e);
        }
    }

}
