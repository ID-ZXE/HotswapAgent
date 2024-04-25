package org.hotswap.agent.servlet;

import org.hotswap.agent.dto.BaseResponse;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.manager.AllExtensionsManager;
import org.hotswap.agent.util.JsonUtils;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

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
        BaseResponse<Object> response;
        boolean locked = false;
        try {
            if (needGlobalLock()) {
                locked = AllExtensionsManager.getInstance().getReentrantLock().tryLock(100, TimeUnit.MILLISECONDS);
                if (!locked) {
                    throw new RuntimeException("当前正在热部署或远程单测执行中，请稍后重试");
                }
            }

            if (!isUploadFile()) {
                body = getRequestBody(req);
            }
            result = doExecute();
            response = BaseResponse.build(result);
        } catch (Exception | Error e) {
            response = BaseResponse.fail(e.getMessage());
        } finally {
            // 如果加锁成功 则释放
            if (locked) {
                AllExtensionsManager.getInstance().getReentrantLock().unlock();
            }
        }

        writeJsonResp(resp, response);
        if (isPrintLog()) {
            LOGGER.info("[agent]收到HTTP请求 uri:{} body:{} params:{} result:{} cost:{}", req.getRequestURI(), body, JsonUtils.toString(req.getParameterMap()), JsonUtils.toString(response), System.currentTimeMillis() - start);
        }
    }

    public abstract Object doExecute() throws Exception;

    protected boolean needGlobalLock() {
        return true;
    }

    protected boolean isUploadFile() {
        return false;
    }

    protected boolean isPrintLog() {
        return true;
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
