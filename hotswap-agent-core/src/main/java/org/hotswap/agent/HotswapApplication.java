package org.hotswap.agent;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.hotswap.agent.config.PluginManager;
import org.hotswap.agent.handle.CompileEngine;
import org.hotswap.agent.manager.ResultManager;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.watch.nio.EventDispatcher;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
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
            httpServer.createContext("/hotswap/dispatch", exchange -> {
                try {
                    HotswapApplication.getInstance().dispatchFile();
                    response(exchange, "ok");
                } catch (Exception e) {
                    response(exchange, "error");
                    LOGGER.error("HotswapApplication handle request failure!!!", e);
                }
            });

            httpServer.createContext("/hotswap/openChannel", exchange -> {
                try {
                    HotswapApplication.getInstance().openChannel();
                    response(exchange, "ok");
                } catch (Exception e) {
                    response(exchange, "error");
                    LOGGER.error("HotswapApplication handle request failure!!!", e);
                }
            });

            httpServer.setExecutor(Executors.newSingleThreadExecutor());
            httpServer.start();
        } catch (Exception e) {
            LOGGER.error("HotswapApplication start failure!!!", e);
        }

    }

    private void response(HttpExchange exchange, String result) throws IOException {
        exchange.sendResponseHeaders(200, 0);
        OutputStream os = exchange.getResponseBody();
        os.write(result.getBytes());
        os.close();
    }

    /**
     * 分发文件
     */
    public void dispatchFile() throws Exception {
        CompileEngine.getInstance().compile();
    }

    /**
     * 开始热部署
     */
    public void openChannel() throws Exception {
        // 启动监控线程
        ResultManager.start();
        // hotswap
        PluginManager.getInstance().hotswap(CompileEngine.getInstance().getCompileResult());
        dispatcher.openChannel();
        // 等待执行结束
        boolean timeout = !dispatcher.getCountDownLatch().await(3L, TimeUnit.MINUTES);
        if (timeout) {
            LOGGER.info("hotswap timeout");
            dispatcher.release();
        }
    }

    /**
     * 热部署结束
     */
    public void markHotswapOver() {
        dispatcher.release();
        dispatcher.getCountDownLatch().countDown();
    }

}
