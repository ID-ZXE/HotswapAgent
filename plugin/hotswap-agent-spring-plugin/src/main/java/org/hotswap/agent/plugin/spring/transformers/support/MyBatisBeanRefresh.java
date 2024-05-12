package org.hotswap.agent.plugin.spring.transformers.support;

import org.hotswap.agent.logging.AgentLogger;
import org.springframework.util.CollectionUtils;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

public class MyBatisBeanRefresh {

    private static final AgentLogger LOGGER = AgentLogger.getLogger(MyBatisBeanRefresh.class);

    public static void swapNewMapper(ClassLoader classLoader, Class<?> classz) {
        if (classz == null || !classz.isInterface()) {
            return;
        }

        List<String> scanBasePackages = Arrays.asList(MyBatisSpringBeanDefinition.scanBasePackages.split(","));

        boolean needSwap = false;
        if (!CollectionUtils.isEmpty(scanBasePackages)) {
            if (scanBasePackages.stream().anyMatch(scanBasePackage -> classz.getName().startsWith(scanBasePackage))) {
                needSwap = true;
            }
        }

        if (!needSwap) {
            return;
        }

        URL resource = classz.getResource("/" + classz.getName().replace('.', '/') + ".class");
        if (resource == null) {
            return;
        }

        String classFilePath = resource.getFile();
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(new File(classFilePath).toPath());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


}
