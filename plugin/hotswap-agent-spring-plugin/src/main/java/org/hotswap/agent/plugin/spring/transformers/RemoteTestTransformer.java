package org.hotswap.agent.plugin.spring.transformers;

import org.hotswap.agent.annotation.LoadEvent;
import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.constants.HotswapConstants;
import org.hotswap.agent.javassist.*;
import org.hotswap.agent.logging.AgentLogger;

public class RemoteTestTransformer {

    private static final AgentLogger LOGGER = AgentLogger.getLogger(RemoteTestTransformer.class);

    @OnClassLoadEvent(classNameRegexp = ".*Test", events = LoadEvent.REDEFINE)
    public static void addRemoteExecuteLog(ClassLoader appClassLoader, CtClass clazz,
                                           ClassPool classPool) throws NotFoundException, CannotCompileException {
        try {
            CtMethod runRemoteTest = clazz.getDeclaredMethod(HotswapConstants.RUN_REMOTE_METHOD_NAME);
            if (runRemoteTest != null) {
                CtMethod[] methods = clazz.getDeclaredMethods();
                for (CtMethod method : methods) {
                    LOGGER.info("patch remote test method {}", method.getName());

                    //method.insertBefore(RemoteTestTransformer.class.getName() + ".remoteInfoLog(\"远程单测：" + method.getName() + "开始执行\");");

                    CtClass ex = ClassPool.getDefault().get("java.lang.Exception");
                    method.addCatch("{ System.out.println($e);throw $e;}", ex);

                    // 创建新的 try-catch 块
//                    String tryCatchBody = "try {\n" +
//                            RemoteTestTransformer.class.getName() + ".remoteInfoLog(\"远程单测：" + method.getName() + "开始执行\");" +
//                            "    $_ = $proceed($$);\n" +
//                            RemoteTestTransformer.class.getName() + ".remoteInfoLog(\"远程单测：" + method.getName() + "执行成功\");" +
//                            "} catch (Exception e) {\n" +
//                            RemoteTestTransformer.class.getName() + ".remoteErrorLog(\"远程单测：" + method.getName() + "执行出现异常\", e);" +
//                            "}";

                    // 修改方法体
//                    method.setBody(tryCatchBody);
                }
            }
        } catch (Exception e) {
            LOGGER.error("patch remote test error", e);
        }
        // LOGGER.info("patch remote test success {}", clazz.getName());
    }

    public static void remoteInfoLog(String message) {
        LOGGER.info(HotswapConstants.REMOTE_TEST_TAG + message);
    }

    public static void remoteErrorLog(String message, Throwable throwable) {
        LOGGER.error(HotswapConstants.REMOTE_TEST_TAG + message, throwable);
    }

}
