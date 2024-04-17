package org.hotswap.agent;

import com.sun.net.httpserver.HttpServer;
import org.hotswap.agent.config.PluginManager;
import org.hotswap.agent.dto.BaseResponse;
import org.hotswap.agent.dto.ReloadResultDTO;
import org.hotswap.agent.handle.AbstractHttpHandler;
import org.hotswap.agent.handle.CompileEngine;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.manager.ResultManager;
import org.hotswap.agent.watch.nio.EventDispatcher;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class HotswapApplication {

    private EventDispatcher dispatcher;

    private static final HotswapApplication INSTANCE = new HotswapApplication();

    private static final AgentLogger LOGGER = AgentLogger.getLogger(HotswapApplication.class);

    public static HotswapApplication getInstance() {
        return INSTANCE;
    }

    public void setDispatcher(EventDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    public void start() {
        try {
            HttpServer httpServer = HttpServer.create(new InetSocketAddress(10888), 0);

            httpServer.createContext("/hotswap/uploadFilesToClasspath", new AbstractHttpHandler() {
                @Override
                public Object execute() {
                    HashMap<Object, Object> result = new HashMap<>();
                    result.put("name", "xxx");
                    return result;
                }
            });

            httpServer.createContext("/hotswap/reloadJar", new AbstractHttpHandler() {
                @Override
                public Object execute() {
                    HashMap<Object, Object> result = new HashMap<>();
                    result.put("name", "xxx");
                    return result;
                }
            });

            httpServer.createContext("/hotswap/reload", new AbstractHttpHandler() {
                @Override
                public Object execute() throws Exception {
                    ReloadResultDTO reloadResultDTO = new ReloadResultDTO();
                    long start = System.currentTimeMillis();
                    long compileCostTime = CompileEngine.getInstance().compile();
                    long reloadCostTime = openChannel();
                    long totalCostTime = System.currentTimeMillis() - start;
                    reloadResultDTO.setCompileCostTime(compileCostTime);
                    reloadResultDTO.setReloadCostTime(reloadCostTime);
                    reloadResultDTO.setTotalCostTime(totalCostTime);
                    reloadResultDTO.setSuccess(true);
                    return BaseResponse.build(reloadResultDTO);
                }
            });

            httpServer.setExecutor(Executors.newSingleThreadExecutor());
            httpServer.start();
        } catch (Exception e) {
            LOGGER.error("HotswapApplication start failure!!!", e);
        }
    }

    /**
     * 开始热部署
     */
    public long openChannel() throws Exception {
        long start = System.currentTimeMillis();
        // 启动监控线程
        ResultManager.start();
        dispatcher.openChannel();
        // hotswap
        PluginManager.getInstance().hotswap(CompileEngine.getInstance().getCompileResult());
        // 等待执行结束
        boolean timeout = !dispatcher.getCountDownLatch().await(3L, TimeUnit.MINUTES);
        if (timeout) {
            LOGGER.info("hotswap timeout");
            dispatcher.release();
        }
        return System.currentTimeMillis() - start;
    }

    /**
     * 热部署结束
     */
    public void markHotswapOver() {
        dispatcher.release();
        dispatcher.getCountDownLatch().countDown();
    }

}
