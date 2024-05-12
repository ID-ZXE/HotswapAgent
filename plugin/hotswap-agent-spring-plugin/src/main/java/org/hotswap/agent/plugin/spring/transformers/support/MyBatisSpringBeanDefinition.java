package org.hotswap.agent.plugin.spring.transformers.support;

import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.manager.AllExtensionsManager;
import org.hotswap.agent.util.spring.util.StringUtils;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.util.ClassUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class MyBatisSpringBeanDefinition {

    private static final AgentLogger LOGGER = AgentLogger.getLogger(MyBatisSpringBeanDefinition.class);

    public static String scanBasePackages;

    public static void loadBasePackages(String basePackages) {
        if (null != scanBasePackages) {
            return;
        }
        scanBasePackages = basePackages;
        AllExtensionsManager.getInstance().setMybatisBasePackage(scanBasePackages);
    }

    public static void loadBasePackages(AnnotationMetadata importingClassMetadata) {
        AnnotationAttributes annoAttrs = AnnotationAttributes.fromMap(importingClassMetadata.getAnnotationAttributes(MapperScan.class.getName()));
        if (Objects.isNull(annoAttrs)) {
            return;
        }

        List<String> basePackages = new ArrayList<>();
        for (String pkg : annoAttrs.getStringArray("value")) {
            if (StringUtils.hasText(pkg)) {
                basePackages.add(pkg);
            }
        }

        for (String pkg : annoAttrs.getStringArray("basePackages")) {
            if (StringUtils.hasText(pkg)) {
                basePackages.add(pkg);
            }
        }
        for (Class<?> clazz : annoAttrs.getClassArray("basePackageClasses")) {
            basePackages.add(ClassUtils.getPackageName(clazz));
        }
        scanBasePackages = String.join(",", basePackages);
        AllExtensionsManager.getInstance().setMybatisBasePackage(scanBasePackages);
    }

}
