package org.hotswap.agent.handle;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.hotswap.agent.logging.AgentLogger;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

public class EmbedHttpServer {

    private static AgentLogger LOGGER = AgentLogger.getLogger(EmbedHttpServer.class);

    public static void start() {
        try {
            HttpServer httpServer = HttpServer.create(new InetSocketAddress(10888), 0);
            httpServer.createContext("/hotswap/dispatch", exchange -> {
                try {
                    LocalCompileHandler.compile();
                    response(exchange, "ok");
                } catch (Exception ex) {
                    response(exchange, "error");
                    LOGGER.error("EmbedHttpServer handle request failure!!!", ex);
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