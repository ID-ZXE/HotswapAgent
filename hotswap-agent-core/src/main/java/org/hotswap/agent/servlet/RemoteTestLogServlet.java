package org.hotswap.agent.servlet;

import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.manager.AgentLogManager;

public class RemoteTestLogServlet extends AbstractHttpServlet {

    private static final AgentLogger LOGGER = AgentLogger.getLogger(RemoteTestLogServlet.class);

    @Override
    public Object doExecute() throws Exception {
        LOGGER.info("[agent] 客户端拉取remote-test日志");
        return AgentLogManager.getInstance().getFirstRemoteTestLog(10);
    }

    @Override
    protected boolean needGlobalLock() {
        return false;
    }

    @Override
    protected boolean isPrintLog() {
        return false;
    }

}
