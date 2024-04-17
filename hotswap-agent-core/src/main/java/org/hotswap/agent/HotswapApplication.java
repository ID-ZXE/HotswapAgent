package org.hotswap.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.hotswap.agent.config.PluginManager;
import org.hotswap.agent.dto.BaseResponse;
import org.hotswap.agent.dto.ReloadResultDTO;
import org.hotswap.agent.handle.CompileEngine;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.manager.ResultManager;
import org.hotswap.agent.util.JsonUtils;
import org.hotswap.agent.watch.nio.EventDispatcher;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
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

            httpServer.createContext("/hotswap/uploadFiles", exchange -> {
                String requestBody = getRequestBody(exchange);
                LOGGER.info("收到文件上传请求，开始执行：{}", requestBody);
                Map<String, String> request = JsonUtils.toObject(requestBody, new TypeReference<Map<String, String>>() {});
                LOGGER.info("request:{}", JsonUtils.toString(request));

                try {
                    responseJson(exchange, JsonUtils.toString(BaseResponse.build()));
                } catch (Exception e) {
                    responseJson(exchange, JsonUtils.toString(BaseResponse.fail(e.getMessage())));
                    LOGGER.error("HotswapApplication handle request failure!!!", e);
                }
            });

            httpServer.createContext("/hotswap/uploadFilesToClasspath", exchange -> {
                try {
                    responseJson(exchange, JsonUtils.toString(BaseResponse.build()));
                } catch (Exception e) {
                    responseJson(exchange, JsonUtils.toString(BaseResponse.fail(e.getMessage())));
                    LOGGER.error("HotswapApplication handle request failure!!!", e);
                }
            });

            httpServer.createContext("/hotswap/reloadJar", exchange -> {
                try {
                    responseJson(exchange, JsonUtils.toString(BaseResponse.build()));
                } catch (Exception e) {
                    responseJson(exchange, JsonUtils.toString(BaseResponse.fail(e.getMessage())));
                    LOGGER.error("HotswapApplication handle request failure!!!", e);
                }
            });

            httpServer.createContext("/hotswap/reload", exchange -> {
                LOGGER.info("收到热部署请求，开始执行");
                BaseResponse<?> baseResponse;
                ReloadResultDTO reloadResultDTO = new ReloadResultDTO();
                try {
                    long start = System.currentTimeMillis();
                    long compileCostTime = CompileEngine.getInstance().compile();
                    long reloadCostTime = openChannel();
                    long totalCostTime = System.currentTimeMillis() - start;
                    reloadResultDTO.setCompileCostTime(compileCostTime);
                    reloadResultDTO.setReloadCostTime(reloadCostTime);
                    reloadResultDTO.setTotalCostTime(totalCostTime);
                    reloadResultDTO.setSuccess(true);
                    baseResponse = BaseResponse.build(reloadResultDTO);
                } catch (Exception e) {
                    baseResponse = BaseResponse.fail(e.getMessage());
                    LOGGER.error("HotswapApplication handle request failure!!!", e);
                }
                responseJson(exchange, JsonUtils.toString(baseResponse));
            });

            httpServer.setExecutor(Executors.newSingleThreadExecutor());
            httpServer.start();
        } catch (Exception e) {
            LOGGER.error("HotswapApplication start failure!!!", e);
        }
    }

    private void responseJson(HttpExchange exchange, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(200, bytes.length);
        OutputStream os = exchange.getResponseBody();
        os.write(bytes);
        os.close();
    }

    private String getRequestBody(HttpExchange exchange) throws IOException {
        InputStream inputStream = exchange.getRequestBody();
        try (ByteArrayOutputStream bas = new ByteArrayOutputStream()) {
            int i;
            while ((i = inputStream.read()) != -1) {
                bas.write(i);
            }
            return bas.toString();
        } catch (IOException e) {
            throw new RuntimeException(e);
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
