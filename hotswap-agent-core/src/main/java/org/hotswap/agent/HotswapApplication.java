package org.hotswap.agent;

import org.hotswap.agent.config.PluginManager;
import org.hotswap.agent.handle.CompileEngine;
import org.hotswap.agent.handle.ResultHandler;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.watch.nio.EventDispatcher;

import java.util.concurrent.TimeUnit;

public class HotswapApplication {

    private EventDispatcher dispatcher;

    private static final HotswapApplication INSTANCE = new HotswapApplication();

    private static final AgentLogger LOGGER = AgentLogger.getLogger(CompileEngine.class);

    public static HotswapApplication getInstance() {
        return INSTANCE;
    }

    public void setDispatcher(EventDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    /**
     * 分发文件
     */
    public void dispatchFile() throws Exception {
        CompileEngine.getInstance().compile();
    }

    /**
     * 开始热部署
     */
    public void openChannel() throws Exception {
        ResultHandler.startResultThread();
        PluginManager.getInstance().hotswap(CompileEngine.getInstance().getCompileResult());
        dispatcher.openChannel();
        boolean timeout = !dispatcher.getCountDownLatch().await(2L, TimeUnit.MINUTES);
        if (timeout) {
            LOGGER.info("hotswap timeout");
            dispatcher.release();
        }
        CompileEngine.getInstance().cleanCompileResult();
    }

    /**
     * 热部署结束
     */
    public void markHotswapOver() {
        dispatcher.getCountDownLatch().countDown();
    }

}
