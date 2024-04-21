package org.hotswap.agent.servlet;

import org.hotswap.agent.config.PluginManager;
import org.hotswap.agent.dto.ReloadResultDTO;
import org.hotswap.agent.handle.CompileEngine;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.manager.ResultManager;
import org.hotswap.agent.watch.nio.EventDispatcher;

import java.util.concurrent.TimeUnit;

public class ReloadServlet extends AbstractHttpServlet {

    private final EventDispatcher dispatcher;

    private static final AgentLogger LOGGER = AgentLogger.getLogger(ReloadServlet.class);

    public ReloadServlet(EventDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @Override
    public synchronized Object doExecute() throws Exception {
        ReloadResultDTO reloadResultDTO = new ReloadResultDTO();
        long start = System.currentTimeMillis();
        long compileCostTime = CompileEngine.getInstance().compile();
        long reloadCostTime = openChannel();
        long totalCostTime = System.currentTimeMillis() - start;
        reloadResultDTO.setCompileCostTime(compileCostTime);
        reloadResultDTO.setReloadCostTime(reloadCostTime);
        reloadResultDTO.setTotalCostTime(totalCostTime);
        reloadResultDTO.setSuccess(true);

        return reloadResultDTO;
    }

    public long openChannel() throws Exception {
        long start = System.currentTimeMillis();
        // 启动监控线程
        ResultManager.start();
        dispatcher.openChannel();
        // hotswap
        PluginManager.getInstance().hotswap(CompileEngine.getInstance().getCompileResult());
        // 等待执行结束
        boolean timeout = !dispatcher.getCountDownLatch().await(3L, TimeUnit.MINUTES);
        if (timeout) {
            LOGGER.info("hotswap timeout");
            dispatcher.release();
        }
        return System.currentTimeMillis() - start;
    }


}
