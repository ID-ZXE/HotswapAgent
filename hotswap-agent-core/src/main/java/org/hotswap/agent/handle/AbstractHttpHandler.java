package org.hotswap.agent.handle;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.hotswap.agent.dto.BaseResponse;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.util.JsonUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;

public abstract class AbstractHttpHandler implements HttpHandler {

    private static final AgentLogger LOGGER = AgentLogger.getLogger(AbstractHttpHandler.class);

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        long start = System.currentTimeMillis();
        URI requestURI = null;
        String params = null;
        String body = null;
        Object result = null;
        try {
            requestURI = exchange.getRequestURI();
            params = requestURI.getQuery();
            body = readBody(exchange);
            result = execute();
            responseJson(exchange, JsonUtils.toString(result));
        } catch (Exception e) {
            responseJson(exchange, JsonUtils.toString(BaseResponse.fail(e.getMessage())));
        }
        LOGGER.info("收到HTTP请求 uri:{} body:{} params:{} result:{} cost:{}",
                requestURI, body, params, JsonUtils.toString(result), System.currentTimeMillis() - start);
    }

    public abstract Object execute() throws Exception;

    protected void responseJson(HttpExchange exchange, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(200, bytes.length);
        OutputStream os = exchange.getResponseBody();
        os.write(bytes);
        os.close();
    }

    protected String readBody(HttpExchange exchange) {
        InputStream inputStream = exchange.getRequestBody();
        try (ByteArrayOutputStream bas = new ByteArrayOutputStream()) {
            int i;
            while ((i = inputStream.read()) != -1) {
                bas.write(i);
            }
            String result = bas.toString();
            if (result == null || result.length() == 0) {
                return null;
            }
            return result;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


}
