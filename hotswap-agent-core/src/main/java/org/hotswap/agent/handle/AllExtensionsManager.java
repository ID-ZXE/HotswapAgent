package org.hotswap.agent.handle;

import org.hotswap.agent.config.PluginConfiguration;
import org.hotswap.agent.logging.AgentLogger;

import java.io.InputStream;
import java.util.Properties;

public class AllExtensionsManager {

    private static final AgentLogger LOGGER = AgentLogger.getLogger(AllExtensionsManager.class);

    private static final AllExtensionsManager INSTANCE = new AllExtensionsManager();

    private ClassLoader classLoader;

    private String app;

    private String profile;

    public static AllExtensionsManager getInstance() {
        return INSTANCE;
    }

    public void setClassLoader(ClassLoader classLoader) {
        this.classLoader = classLoader;
        setAppInfo();
        PluginConfiguration.initExtraClassPath(AllExtensionsManager.getInstance().getClassLoader());
    }

    public ClassLoader getClassLoader() {
        return this.classLoader;
    }

    public void setAppInfo() {
        try {
            InputStream resourceAsStream = this.classLoader.getResourceAsStream("application.properties");
            Properties properties = new Properties();
            properties.load(resourceAsStream);

            this.app = properties.getProperty("spring.application.name");
            LOGGER.info("app is {} ", app);
        } catch (Exception e) {
            LOGGER.error("setAppInfo failure", e);
        }
    }

    public String getApp() {
        return app;
    }

    public String getProfile() {
        return profile;
    }

    public void setProfile(String... profiles) {
        if (profiles.length == 0) {
            LOGGER.info("profile not exists, set profile is default");
            return;
        }
        this.profile = profiles[0];
        LOGGER.info("profile is {} ", profile);
    }

}
