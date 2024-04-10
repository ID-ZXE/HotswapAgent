package org.hotswap.agent.handler;

import org.hotswap.agent.handle.AllExtensionsManager;
import org.hotswap.agent.handle.CompileEngine;
import org.junit.Test;

public class LocalCompileHandlerTest {

    @Test
    public void test1() throws Exception {
        CompileEngine.getInstance().cleanOldClassFile();
        AllExtensionsManager.getInstance().setClassLoader(Thread.currentThread().getContextClassLoader());
        CompileEngine.getInstance().compile();
    }

}
