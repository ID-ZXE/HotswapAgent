package org.hotswap.agent.manager;

import org.hotswap.agent.HotswapApplication;
import org.hotswap.agent.command.Command;
import org.hotswap.agent.logging.AgentLogger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

public class ResultManager {

    private static final ReentrantLock LOCK = new ReentrantLock();

    private static final List<Thread> THREAD_LIST = new ArrayList<>();

    private static final List<Thread> SLOW_THREAD_LIST = new ArrayList<>();

    private static final AgentLogger LOGGER = AgentLogger.getLogger(ResultManager.class);

    public static void start() {
        new Thread(new ResultThread()).start();
    }

    public static void cleanTheadList() {
        LOCK.lock();
        try {
            LOGGER.info("清空THREAD");
            THREAD_LIST.clear();
            SLOW_THREAD_LIST.clear();
        } finally {
            LOCK.unlock();
        }
    }

    public static synchronized void addToResulManager(Command command, Thread thread) {
        if (!LOCK.isLocked()) {
            LOGGER.info("[agent] add command:{} thread:{} to THREAD_LIST", command, thread.getName());
            THREAD_LIST.add(thread);
        } else {
            LOGGER.info("[agent] add command:{} thread:{} to SLOW_THREAD_LIST", command, thread.getName());
            SLOW_THREAD_LIST.add(thread);
        }
    }

    private static class ResultThread implements Runnable {

        @Override
        public void run() {
            cleanTheadList();
            try {
                long start = System.currentTimeMillis();
                // 等待所有listener注册完毕
                Thread.sleep(4500);
                LOCK.lock();
                for (Thread thread : THREAD_LIST) {
                    // 等待所有线程执行完毕
                    thread.join(60 * 1000);
                    LOGGER.info("[agent] {} finished", thread.getName());
                }
                // Thread.sleep(3000);
                int retry = 0;
                while (true) {
                    try {
                        if (SLOW_THREAD_LIST.size() > 0) {
                            LOGGER.warning("存在慢线程，处理中请等待！！！");
                            for (Thread thread : SLOW_THREAD_LIST) {
                                thread.join(60 * 1000);
                            }
                            break;
                        }
                        break;
                    } catch (Exception ignore) {
                        retry++;
                        if (retry > 3) {
                            break;
                        }
                    }
                }

                cleanTheadList();
                LOGGER.info("热部署结束 耗时:{}", System.currentTimeMillis() - start);
            } catch (Exception e) {
                LOGGER.info("ResultManager has error", e);
            } finally {
                if (LOCK.isLocked()) {
                    LOCK.unlock();
                }
                HotswapApplication.getInstance().markHotswapOver();
            }
        }

    }

}
