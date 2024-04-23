package org.hotswap.agent.servlet;

import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.manager.AgentLogManager;

public class LogServlet extends AbstractHttpServlet {

    private static final AgentLogger LOGGER = AgentLogger.getLogger(LogServlet.class);

    @Override
    public Object doExecute() throws Exception {
        LOGGER.info("[agent] 客户端拉取日志");
        return AgentLogManager.getInstance().getFirstLog(10);
    }

    @Override
    protected boolean isPrintLog() {
        return false;
    }
}
