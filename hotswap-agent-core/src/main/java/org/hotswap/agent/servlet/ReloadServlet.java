package org.hotswap.agent.servlet;

import org.hotswap.agent.HotswapApplication;
import org.hotswap.agent.dto.ReloadResultDTO;
import org.hotswap.agent.handle.CompileEngine;
import org.hotswap.agent.logging.AgentLogger;

public class ReloadServlet extends AbstractHttpServlet {

    private static final AgentLogger LOGGER = AgentLogger.getLogger(ReloadServlet.class);

    @Override
    public synchronized Object doExecute() throws Exception {
        ReloadResultDTO reloadResultDTO = new ReloadResultDTO();
        long start = System.currentTimeMillis();
        long compileCostTime = CompileEngine.getInstance().compile();
        long reloadCostTime = HotswapApplication.getInstance().openChannel();
        long totalCostTime = System.currentTimeMillis() - start;
        reloadResultDTO.setCompileCostTime(compileCostTime);
        reloadResultDTO.setReloadCostTime(reloadCostTime);
        reloadResultDTO.setTotalCostTime(totalCostTime);
        reloadResultDTO.setSuccess(true);

        return reloadResultDTO;
    }

}
