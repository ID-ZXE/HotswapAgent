package org.hotswap.agent;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.hotswap.agent.config.PluginManager;
import org.hotswap.agent.handle.CompileEngine;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.manager.AllExtensionsManager;
import org.hotswap.agent.manager.ResultManager;
import org.hotswap.agent.servlet.*;
import org.hotswap.agent.watch.nio.EventDispatcher;

import java.util.Map;
import java.util.concurrent.TimeUnit;

public class HotswapApplication {

    private static final AgentLogger LOGGER = AgentLogger.getLogger(HotswapApplication.class);

    private EventDispatcher dispatcher;

    private static final HotswapApplication INSTANCE = new HotswapApplication();

    public static HotswapApplication getInstance() {
        return INSTANCE;
    }

    public void setDispatcher(EventDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    public void start() {
        try {
            Server server = new Server(AllExtensionsManager.getInstance().getEmbedJettyPort());

            ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
            context.setContextPath("/hotswap");
            server.setHandler(context);

            context.addServlet(new ServletHolder(new UploadReloadFileServlet()), "/uploadReloadFile");
            context.addServlet(new ServletHolder(new ReloadClassServlet()), "/reloadClass");
            context.addServlet(new ServletHolder(new ReloadJarServlet()), "/reloadJar");
            context.addServlet(new ServletHolder(new ReloadServlet()), "/reload");
            context.addServlet(new ServletHolder(new LogServlet()), "/log");

            server.start();
        } catch (Exception e) {
            throw new RuntimeException("yyr-agent inner jetty server start failure", e);
        }
    }

    public long openChannel() throws Exception {
        long start = System.currentTimeMillis();
        // 启动监控线程
        ResultManager.start();
        dispatcher.openChannel();
        printBanner();
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

    /**
     * 热部署结束
     */
    public void markHotswapOver() {
        dispatcher.release();
        dispatcher.getCountDownLatch().countDown();
    }

    public boolean channelIsOpen() {
        return dispatcher != null && dispatcher.channelIsOpen();
    }

    private static void printBanner() {
        LOGGER.info("\n" +
                "__     ____     _______        _    _  ____ _______ _______          __     _____  \n" +
                " \\ \\   / /\\ \\   / /  __ \\      | |  | |/ __ \\__   __/ ____\\ \\        / /\\   |  __ \\ \n" +
                "  \\ \\_/ /  \\ \\_/ /| |__) |_____| |__| | |  | | | | | (___  \\ \\  /\\  / /  \\  | |__) |\n" +
                "   \\   /    \\   / |  _  /______|  __  | |  | | | |  \\___ \\  \\ \\/  \\/ / /\\ \\ |  ___/ \n" +
                "    | |      | |  | | \\ \\      | |  | | |__| | | |  ____) |  \\  /\\  / ____ \\| |     \n" +
                "    |_|      |_|  |_|  \\_\\     |_|  |_|\\____/  |_| |_____/    \\/  \\/_/    \\_\\_|     \n" +
                "                                                                                    ");
    }

}
