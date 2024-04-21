package org.hotswap.agent.servlet;

import org.hotswap.agent.HotswapApplication;
import org.hotswap.agent.dto.ReloadResultDTO;
import org.hotswap.agent.handle.CompileEngine;
import org.hotswap.agent.manager.AgentLogManager;

public class ReloadServlet extends AbstractHttpServlet {

    @Override
    public synchronized Object doExecute() throws Exception {
        // 清空日志
        AgentLogManager.getInstance().cleanLog();

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
