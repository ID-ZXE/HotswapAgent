package org.hotswap.agent.manager;

import org.hotswap.agent.logging.AgentLogger;

import java.io.InputStream;
import java.util.Properties;

import static org.hotswap.agent.util.JarUtils.createLombokJar;

public class AllExtensionsManager {

    private static final AgentLogger LOGGER = AgentLogger.getLogger(AllExtensionsManager.class);

    private static final AllExtensionsManager INSTANCE = new AllExtensionsManager();

    private volatile ClassLoader classLoader;

    private volatile boolean hasPrependClassPath = false;

    private volatile String app;

    private volatile String profile;

    public static AllExtensionsManager getInstance() {
        return INSTANCE;
    }

    public void setClassLoader(ClassLoader classLoader) {
        this.classLoader = classLoader;
        LOGGER.info("ClassLoader 初始化 {}", classLoader);
        setAppInfo();
        createLombokJar();

//        try {
//            File lombokJar = new File(HotswapConstants.EXT_CLASS_PATH, "lombok.jar");
//            URLClassPathHelper.prependClassPath(AllExtensionsManager.getInstance().getClassLoader(), new URL[]{lombokJar.toURI().toURL()});
//        } catch (Exception e) {
//            LOGGER.error("createLombokJar error", e);
//        }
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

}
