package org.hotswap.agent.manager;

import org.hotswap.agent.HotswapApplication;
import org.hotswap.agent.constants.HotswapConstants;
import org.hotswap.agent.handle.CompileEngine;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class AgentLogManager {

    private final LinkedList<String> agentLogList = new LinkedList<>();

    private final LinkedList<String> remoteLogList = new LinkedList<>();

    private volatile boolean isRemoteTesting = false;

    private static final AgentLogManager INSTANCE = new AgentLogManager();

    public static AgentLogManager getInstance() {
        return INSTANCE;
    }

    public void cleanLog() {
        agentLogList.clear();
    }

    public void appendLog(String log) {
        if (isRemoteTesting && log.contains(HotswapConstants.REMOTE_TEST_TAG)) {
            remoteLogList.addLast(log);
        }

        boolean isLog = HotswapApplication.getInstance().channelIsOpen()
                || CompileEngine.getInstance().isCompiling();
        if (!isLog) {
            return;
        }
        if (log.contains("[agent]")) {
            return;
        }

        agentLogList.addLast(log);
    }

    public boolean isRemoteTesting() {
        return isRemoteTesting;
    }

    public void setRemoteTesting(boolean remoteTesting) {
        isRemoteTesting = remoteTesting;
    }

    /**
     * 获取前size条日志 并删除
     */
    public List<String> getFirstAgentLog(int size) {
        List<String> result = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            if (agentLogList.isEmpty()) {
                return result;
            }
            String log = agentLogList.removeFirst();
            result.add(log);
        }
        return result;
    }

    public List<String> getFirstRemoteTestLog(int size) {
        List<String> result = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            if (remoteLogList.isEmpty()) {
                return result;
            }
            String log = remoteLogList.removeFirst();
            result.add(log);
        }
        return result;
    }

}
