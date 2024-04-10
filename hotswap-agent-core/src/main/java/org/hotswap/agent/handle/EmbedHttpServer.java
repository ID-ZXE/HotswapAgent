package org.hotswap.agent.handle;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.hotswap.agent.HotswapApplication;
import org.hotswap.agent.logging.AgentLogger;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

public class EmbedHttpServer {

    private static final AgentLogger LOGGER = AgentLogger.getLogger(EmbedHttpServer.class);

    public static void start() {
        try {
            HttpServer httpServer = HttpServer.create(new InetSocketAddress(10888), 0);
            httpServer.createContext("/hotswap/dispatch", exchange -> {
                try {
                    HotswapApplication.getInstance().dispatchFile();
                    response(exchange, "ok");
                } catch (Exception e) {
                    response(exchange, "error");
                    LOGGER.error("EmbedHttpServer handle request failure!!!", e);
                }
            });

            httpServer.createContext("/hotswap/openChannel", exchange -> {
                try {
                    HotswapApplication.getInstance().openChannel();
                    response(exchange, "ok");
                } catch (Exception e) {
                    response(exchange, "error");
                    LOGGER.error("EmbedHttpServer handle request failure!!!", e);
                }
            });

            httpServer.setExecutor(Executors.newSingleThreadExecutor());
            httpServer.start();
        } catch (Exception e) {
            LOGGER.error("EmbedHttpServer start failure!!!", e);
        }

    }

    private static void response(HttpExchange exchange, String result) throws IOException {
        exchange.sendResponseHeaders(200, 0);
        OutputStream os = exchange.getResponseBody();
        os.write(result.getBytes());
        os.close();
    }

}