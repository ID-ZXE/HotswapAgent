package org.hotswap.agent.plugin.spring.core;

import org.apache.dubbo.config.ServiceConfig;
import org.apache.dubbo.config.spring.ServiceBean;
import org.apache.dubbo.config.spring.beans.factory.annotation.ReferenceAnnotationBeanPostProcessor;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.spring.transformers.support.DubboSupport;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class DubboProcessor {

    private static final AgentLogger LOGGER = AgentLogger.getLogger(DubboProcessor.class);

    public static DefaultListableBeanFactory beanFactory;

    public static void reset(DefaultListableBeanFactory beanFactory, Set<String> beansToProcess, Set<String> newBeanNames) {
        Set<String> allBeans = new HashSet<>();
        allBeans.addAll(beansToProcess);
        allBeans.addAll(newBeanNames);

        try {
            Map<String, ReferenceAnnotationBeanPostProcessor> postProcessors = beanFactory.getBeansOfType(ReferenceAnnotationBeanPostProcessor.class);
            if (postProcessors.isEmpty()) {
                LOGGER.debug("ReferenceAnnotationBeanPostProcessor not exist");
                return;
            }

            ReferenceAnnotationBeanPostProcessor postProcessor = postProcessors.values().iterator().next();
            resetAnnotationBeanPostProcessorCache(postProcessor);
            for (String beanName : allBeans) {
                Object object = beanFactory.getSingleton(beanName);
                if (object != null) {
                    postProcessor.postProcessPropertyValues(null, null, object, beanName);

                    for (Class<?> anInterface : object.getClass().getInterfaces()) {
                        ServiceConfig serviceBean = DubboSupport.getServiceBean(anInterface.getName());
                        if (Objects.nonNull(serviceBean)) {
                            DubboSupport.doInvokeApacheDubbo((ServiceBean) serviceBean, object);
                        }
                    }
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
            @SuppressWarnings("all")
            Map injectionMetadataCache = (Map) field.get(object);
            injectionMetadataCache.clear();
            LOGGER.trace("Cache cleared: AutowiredAnnotationBeanPostProcessor/CommonAnnotationBeanPostProcessor" + " injectionMetadataCache");
        } catch (Exception e) {
            throw new IllegalStateException("Unable to clear " + "AutowiredAnnotationBeanPostProcessor/CommonAnnotationBeanPostProcessor injectionMetadataCache", e);
        }
    }

}
