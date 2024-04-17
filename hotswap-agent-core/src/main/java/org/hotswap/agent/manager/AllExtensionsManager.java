package org.hotswap.agent.manager;

import org.hotswap.agent.constants.HotswapConstants;
import org.hotswap.agent.logging.AgentLogger;

import java.io.InputStream;
import java.net.URL;
import java.util.Properties;

import static org.hotswap.agent.config.PluginConfiguration.PLUGIN_CONFIGURATION;

public class AllExtensionsManager {

    private static final AgentLogger LOGGER = AgentLogger.getLogger(AllExtensionsManager.class);

    private static final AllExtensionsManager INSTANCE = new AllExtensionsManager();

    private volatile ClassLoader classLoader;

    private volatile boolean hasPrependClassPath = false;

    private volatile String app;

    private volatile String profile;

    private volatile Properties COMMON_PROPERTIES = new Properties();

    public static AllExtensionsManager getInstance() {
        return INSTANCE;
    }

    public void setClassLoader(ClassLoader classLoader) {
        this.classLoader = classLoader;
        LOGGER.info("ClassLoader 初始化 {}", classLoader);
        setAppInfo();
    }

    public boolean hasPrependClassPath() {
        return hasPrependClassPath;
    }

    public void setHasPrependClassPath(boolean hasPrependClassPath) {
        this.hasPrependClassPath = hasPrependClassPath;
    }

    public ClassLoader getClassLoader() {
        return this.classLoader;
    }

    public String getApp() {
        return app;
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

    public void initProperties() {
        COMMON_PROPERTIES = new Properties();
        URL configurationURL = ClassLoader.getSystemResource(PLUGIN_CONFIGURATION);
        try {
            COMMON_PROPERTIES.load(configurationURL.openStream());
            COMMON_PROPERTIES.putAll(System.getProperties());
        } catch (Exception e) {
            LOGGER.error("Error while loading 'hotswap-agent.properties' from base URL " + configurationURL, e);
        }
    }

    public String getExtraClassPath() {
        return COMMON_PROPERTIES.getProperty(HotswapConstants.EXTRA_CLASSPATH_KEY);
    }

    public String getSourceDirPath() {
        return COMMON_PROPERTIES.getProperty(HotswapConstants.SOURCE_DIR_KEY);
    }

    public String getBaseDirPath() {
        return COMMON_PROPERTIES.getProperty(HotswapConstants.BASE_DIR_KEY);
    }

    public Boolean getLogToConsole() {
        try {
            return Boolean.parseBoolean(COMMON_PROPERTIES.getProperty(HotswapConstants.LOG_TO_CONSOLE_KEY));
        } catch (Exception ignore) {
            return false;
        }
    }

}
