package org.hotswap.agent.handle;

import org.hotswap.agent.config.PluginConfiguration;
import org.hotswap.agent.constants.HotswapConstants;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.util.JarUtils;
import org.hotswap.agent.util.classloader.URLClassPathHelper;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.util.Properties;

public class AllExtensionsManager {

    private static AgentLogger LOGGER = AgentLogger.getLogger(AllExtensionsManager.class);

    private static ClassLoader classLoader;

    private static String app;

    private static String profile;

    public static void setClassLoader(ClassLoader classLoader) {
        AllExtensionsManager.classLoader = classLoader;

        PluginConfiguration.initExtraClassPath(AllExtensionsManager.getClassLoader());
        try {
            URL url = new URL("file:" + HotswapConstants.EXT_CLASS_PATH);
            File lombokJar = JarUtils.createLombokJar();
            URLClassPathHelper.prependClassPath(AllExtensionsManager.getClassLoader(), new URL[]{url, lombokJar.toURI().toURL()});
        } catch (Exception e) {
            LOGGER.error("createLombokJar error", e);
        }

        setAppInfo();
    }

    public static ClassLoader getClassLoader() {
        return AllExtensionsManager.classLoader;
    }

    public static void setAppInfo() {
        try {
            InputStream resourceAsStream = AllExtensionsManager.classLoader.getResourceAsStream("application.properties");
            Properties properties = new Properties();
            properties.load(resourceAsStream);

            AllExtensionsManager.app = properties.getProperty("spring.application.name");
            LOGGER.info("app init {} ", app);
        } catch (Exception e) {
            LOGGER.error("setAppInfo failure", e);
        }
    }

    public static String getApp() {
        return app;
    }

    public static String getProfile() {
        return profile;
    }

    public static void setProfile(String... profiles) {
        if (profiles.length == 0) {
            LOGGER.info("profile not exists, set profile is default");
            return;
        }
        AllExtensionsManager.profile = profiles[0];
        LOGGER.info("profile init {} ", profile);
    }

}
