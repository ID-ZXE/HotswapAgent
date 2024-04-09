package org.hotswap.agent.handle;

import org.hotswap.agent.logging.AgentLogger;

import java.util.ArrayList;
import java.util.List;

public class ResultHandler {

    private static List<Thread> threadList = new ArrayList<>();

    private static AgentLogger LOGGER = AgentLogger.getLogger(LocalCompileHandler.class);

    public static void startResultThread() {
        new Thread(new ResultThread()).start();
    }

    public static void cleanTheadList() {
        threadList.clear();
    }

    public static synchronized void addToResulThread(Thread thread) {
        threadList.add(thread);
    }

    private static class ResultThread implements Runnable {

        @Override
        public void run() {
            try {
                long start = System.currentTimeMillis();
                // 等待所有listener注册完毕
                Thread.sleep(4500);
                for (Thread thread : threadList) {
                    // 等待所有线程执行完毕
                    thread.join(60 * 1000);
                }
                cleanTheadList();
                LOGGER.info("热部署结束 耗时:{}", System.currentTimeMillis() - start);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

    }

}
