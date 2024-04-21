package org.hotswap.agent;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.hotswap.agent.manager.AllExtensionsManager;
import org.hotswap.agent.servlet.ReloadJarServlet;
import org.hotswap.agent.servlet.ReloadServlet;
import org.hotswap.agent.watch.nio.EventDispatcher;

public class HotswapApplication {

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

            context.addServlet(new ServletHolder(new ReloadJarServlet()), "/reloadJar");
            context.addServlet(new ServletHolder(new ReloadServlet(dispatcher)), "/reload");

            server.start();
        } catch (Exception e) {
            throw new RuntimeException("yyr-agent inner jetty server start failure", e);
        }
    }

    /**
     * 热部署结束
     */
    public void markHotswapOver() {
        dispatcher.release();
        dispatcher.getCountDownLatch().countDown();
    }

}
