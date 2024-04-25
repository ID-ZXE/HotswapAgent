package org.hotswap.agent.plugin.spring.transformers;

import org.hotswap.agent.annotation.LoadEvent;
import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.constants.HotswapConstants;
import org.hotswap.agent.javassist.*;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.util.JsonUtils;

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
                    if (!method.getName().startsWith(HotswapConstants.REMOTE_TEST_METHOD_PREFIX)) {
                        return;
                    }

                    boolean isVoid = method.getReturnType().equals(CtClass.voidType);
                    if (isVoid) {
                        method.insertBefore(RemoteTestTransformer.class.getName() + ".remoteInfoLog(\"远程单测：" + method.getName() + "开始执行\");");
                        method.insertAfter(RemoteTestTransformer.class.getName() + ".remoteInfoLog(\"远程单测：" + method.getName() + "执行结束\");");

                        CtClass ex = ClassPool.getDefault().get("java.lang.Exception");
                        method.addCatch("{ "
                                + RemoteTestTransformer.class.getName() + ".remoteErrorLog(\"远程单测：" + method.getName() + "执行出现异常\", $e);"
                                + "return;" +
                                "}", ex);
                    } else {
                        method.insertBefore(RemoteTestTransformer.class.getName() + ".remoteInfoLog(\"远程单测：" + method.getName() + "开始执行\");");
                        method.insertAfter(RemoteTestTransformer.class.getName() + ".remoteInfoLog(\"远程单测：" + method.getName() + "执行结束,返回结果:{}\", $_);");

                        CtClass ex = ClassPool.getDefault().get("java.lang.Exception");
                        method.addCatch("{ "
                                + RemoteTestTransformer.class.getName() + ".remoteErrorLog(\"远程单测：" + method.getName() + "执行出现异常\", $e);"
                                + "return null;" +
                                "}", ex);
                    }
                    LOGGER.info("patch remote test method {} success", method.getName());
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

    public static void remoteInfoLog(String message, Object obj) {
        LOGGER.info(HotswapConstants.REMOTE_TEST_TAG + message, JsonUtils.toString(obj));
    }

    public static void remoteErrorLog(String message, Throwable throwable) {
        LOGGER.error(HotswapConstants.REMOTE_TEST_TAG + message, throwable);
    }

}
