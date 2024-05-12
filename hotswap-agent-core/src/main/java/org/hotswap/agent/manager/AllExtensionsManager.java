package org.hotswap.agent.manager;

import org.hotswap.agent.config.PluginConfiguration;
import org.hotswap.agent.constants.HotswapConstants;
import org.hotswap.agent.dto.BaseResponse;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.util.HttpUtils;
import org.hotswap.agent.util.IpUtils;

import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public class AllExtensionsManager {

    private static final AgentLogger LOGGER = AgentLogger.getLogger(AllExtensionsManager.class);

    private static final AllExtensionsManager INSTANCE = new AllExtensionsManager();

    private final ReentrantLock reentrantLock = new ReentrantLock();

    private volatile ClassLoader classLoader;

    private volatile String springbootBasePackage;

    private volatile String mybatisBasePackage;

    private volatile boolean serverIsRunning = false;

    private volatile boolean hasPrependClassPath = false;

    /**
     * 应用名称 取自spring.application.name
     */
    private volatile String app;

    /**
     * 环境、Spring Profile
     */
    private volatile String profile;

    private volatile Properties COMMON_PROPERTIES = new Properties();

    public ReentrantLock getReentrantLock() {
        return reentrantLock;
    }

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
            this.profile = "default";
            LOGGER.info("profile not exists, set profile is default");
        } else {
            this.profile = profiles[0];
            LOGGER.info("profile is {} ", profile);
        }

        ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
        executorService.scheduleAtFixedRate(() -> {
            try {
                // registerInfo();
            } catch (Exception e) {
                LOGGER.error("[agent] register route error", e);
            }
        }, 0, 20, TimeUnit.SECONDS);
    }

    public void setSpringbootBasePackage(String springbootClass) {
        this.springbootBasePackage = springbootClass.substring(0, springbootClass.lastIndexOf("."));
        LOGGER.info("springbootBasePackage is {}", this.springbootBasePackage);
    }

    public String getSpringbootBasePackage() {
        return springbootBasePackage;
    }

    public String getMybatisBasePackage() {
        return mybatisBasePackage;
    }

    public void setMybatisBasePackage(String mybatisBasePackage) {
        this.mybatisBasePackage = mybatisBasePackage;
        LOGGER.info("mybatisBasePackage is {}", this.mybatisBasePackage);
    }

    public void setServerIsRunning(boolean serverIsRunning) {
        LOGGER.info("Server启动状态变更为:{}", serverIsRunning);
        this.serverIsRunning = serverIsRunning;
    }

    public boolean getServerIsRunning() {
        return serverIsRunning;
    }

    private void registerInfo() {
        if (!serverIsRunning) {
            return;
        }

        String lane = System.getProperty("lane", "default");
        Map<String, Object> params = new HashMap<>();
        params.put("app", this.app);
        params.put("profile", this.profile);
        params.put("lane", System.getProperty("lane", "default"));
        params.put("ip", IpUtils.getLocalIp());

        BaseResponse<?> response = HttpUtils.get("http://localhost:8080/hotswap/register", params);
        if (response == null || !response.isSuccess()) {
            LOGGER.error("registerInfo failure app:{}, profile:{}, lane:{}, ip:{}", this.app, this.profile, lane, IpUtils.getLocalIp());
        } else {
            LOGGER.info("registerInfo success app:{}, profile:{}, lane:{}, ip:{}", this.app, this.profile, lane, IpUtils.getLocalIp());
        }
    }

    public void initProperties() {
        COMMON_PROPERTIES = new Properties();
        URL configurationURL = ClassLoader.getSystemResource(PluginConfiguration.PLUGIN_CONFIGURATION);
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

    public String getJarDirPath() {
        return COMMON_PROPERTIES.getProperty(HotswapConstants.JAR_DIR_KEY);
    }

    public Integer getEmbedJettyPort() {
        String port = COMMON_PROPERTIES.getProperty(HotswapConstants.EMBED_JETTY_PORT);
        return Integer.parseInt(port);
    }

    public Boolean getLogToConsole() {
        try {
            return Boolean.parseBoolean(COMMON_PROPERTIES.getProperty(HotswapConstants.LOG_TO_CONSOLE_KEY));
        } catch (Exception ignore) {
            return false;
        }
    }

}
