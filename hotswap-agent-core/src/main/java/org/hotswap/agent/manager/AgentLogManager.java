package org.hotswap.agent.manager;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class AgentLogManager {

    private final LinkedList<String> logList = new LinkedList<>();

    private static final AgentLogManager INSTANCE = new AgentLogManager();

    public static AgentLogManager getInstance() {
        return INSTANCE;
    }

    public void cleanLog() {
        logList.clear();
    }

    public void appendLog(String log) {
        if (log.contains("AbstractHttpServlet")) {
            return;
        }

        logList.addLast(log);
    }

    /**
     * 获取前size条日志 并删除
     */
    public List<String> getFirstLog(int size) {
        List<String> result = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            if (logList.isEmpty()) {
                return result;
            }
            String log = logList.removeFirst();
            result.add(log);
        }
        return result;
    }

}
