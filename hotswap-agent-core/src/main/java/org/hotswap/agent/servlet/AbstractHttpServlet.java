package org.hotswap.agent.servlet;

import org.hotswap.agent.dto.BaseResponse;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.util.JsonUtils;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;

public abstract class AbstractHttpServlet extends HttpServlet {

    private static final AgentLogger LOGGER = AgentLogger.getLogger(ReloadJarServlet.class);

    protected String body;

    protected HttpServletRequest req;

    protected HttpServletResponse resp;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        execute(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        execute(req, resp);
    }

    private void execute(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        long start = System.currentTimeMillis();
        this.req = req;
        this.resp = resp;
        Object result = null;
        try {
            if (!isUploadFile()) {
                body = getRequestBody(req);
            }
            result = doExecute();
        } catch (Exception e) {
            writeJsonResp(resp, BaseResponse.fail(e.getMessage()));
            LOGGER.error("收到HTTP请求 FAILURE, uri:{} body:{} params:{} result:{} cost:{}", e, req.getRequestURI(), body, JsonUtils.toString(req.getParameterMap()), JsonUtils.toString(result), System.currentTimeMillis() - start);
            return;
        }
        writeJsonResp(resp, BaseResponse.build(result));
        LOGGER.info("收到HTTP请求 SUCCESS, " +
                        "uri:{} " +
                        "body:{} " +
                        "params:{} " +
                        "result:{} " +
                        "cost:{}",
                req.getRequestURI(),
                body,
                JsonUtils.toString(req.getParameterMap()),
                JsonUtils.toString(result),
                System.currentTimeMillis() - start);
    }

    public abstract Object doExecute() throws Exception;

    protected boolean isUploadFile() {
        return false;
    }

    private String getRequestBody(HttpServletRequest req) throws IOException {
        BufferedReader reader = req.getReader();
        StringBuilder body = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            body.append(line);
        }
        return body.toString();
    }

    private void writeJsonResp(HttpServletResponse resp, Object result) throws IOException {
        resp.setHeader("Content-Type", "application/json; charset=utf-8");
        resp.getWriter().println(JsonUtils.toString(result));
    }

}
