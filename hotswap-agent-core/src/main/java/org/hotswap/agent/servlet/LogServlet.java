package org.hotswap.agent.servlet;

import org.hotswap.agent.manager.AgentLogManager;

public class LogServlet extends AbstractHttpServlet {

    @Override
    public Object doExecute() throws Exception {
        return AgentLogManager.getInstance().getFirstLog(10);
    }

    @Override
    protected boolean isPrintLog() {
        return false;
    }
}
