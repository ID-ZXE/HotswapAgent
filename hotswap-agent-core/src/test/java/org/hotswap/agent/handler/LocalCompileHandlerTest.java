package org.hotswap.agent.handler;

import org.hotswap.agent.handle.AllExtensionsManager;
import org.hotswap.agent.handle.LocalCompileHandler;
import org.junit.Test;

public class LocalCompileHandlerTest {

    @Test
    public void test1() throws Exception {
        LocalCompileHandler.cleanOldClassFile();
        AllExtensionsManager.setClassLoader(Thread.currentThread().getContextClassLoader());
        LocalCompileHandler.compile();
    }

}
