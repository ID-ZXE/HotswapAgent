package org.hotswap.agent.plugin.spring.transformers;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import org.hotswap.agent.annotation.LoadEvent;
import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.constants.HotswapConstants;
import org.hotswap.agent.javassist.*;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.util.JsonUtils;

public class RemoteTestTransformer {

    private static final AgentLogger LOGGER = AgentLogger.getLogger(RemoteTestTransformer.class);

    @OnClassLoadEvent(classNameRegexp = "ch.qos.logback.classic.Logger", events = LoadEvent.DEFINE)
    public static void patchLogger(ClassLoader appClassLoader, CtClass clazz, ClassPool classPool) {
        try {
            CtMethod callAppenders = clazz.getDeclaredMethod("callAppenders");
            callAppenders.insertBefore("if(org.hotswap.agent.watch.nio.EventDispatcher.remoteTestLogIsOpen()) " +
                    "{" + RemoteTestTransformer.class.getName() + ".remoteInfoLog($1);}");
        } catch (Exception e) {
            LOGGER.error("远程单元测试增加日志切面失败");
        }
    }

    @OnClassLoadEvent(classNameRegexp = ".*Test.*", events = LoadEvent.REDEFINE)
    public static void addRemoteExecuteLog(ClassLoader appClassLoader, CtClass clazz,
                                           ClassPool classPool) throws NotFoundException, CannotCompileException {
        if (clazz.getName().contains("$")) {
            return;
        }
        try {
            CtMethod runRemoteTest = clazz.getDeclaredMethod(HotswapConstants.RUN_REMOTE_METHOD_NAME);
            if (runRemoteTest != null) {
                CtMethod[] methods = clazz.getDeclaredMethods();
                for (CtMethod method : methods) {
                    if (!method.getName().startsWith(HotswapConstants.REMOTE_TEST_METHOD_PREFIX)) {
                        return;
                    }

                    String realName = method.getName().replace(HotswapConstants.REMOTE_TEST_METHOD_PREFIX, "");

                    boolean isVoid = method.getReturnType().equals(CtClass.voidType);
                    if (isVoid) {
                        method.insertBefore(RemoteTestTransformer.class.getName() + ".remoteInfoLog(\"远程单测：" + realName + "开始执行\");");
                        method.insertAfter(RemoteTestTransformer.class.getName() + ".remoteInfoLog(\"远程单测：" + realName + "执行结束\");");

                        CtClass ex = ClassPool.getDefault().get("java.lang.Exception");
                        method.addCatch("{ "
                                + RemoteTestTransformer.class.getName() + ".remoteErrorLog(\"远程单测：" + realName + "执行出现异常\", $e);"
                                + "return;" +
                                "}", ex);
                    } else {
                        method.insertBefore(RemoteTestTransformer.class.getName() + ".remoteInfoLog(\"远程单测：" + realName + "开始执行\");");
                        method.insertAfter(RemoteTestTransformer.class.getName() + ".remoteInfoLog(\"远程单测：" + realName + "执行结束,返回结果:{}\", $_);");

                        CtClass ex = ClassPool.getDefault().get("java.lang.Exception");
                        method.addCatch("{ "
                                + RemoteTestTransformer.class.getName() + ".remoteErrorLog(\"远程单测：" + realName + "执行出现异常\", $e);"
                                + "return null;" +
                                "}", ex);
                    }
                    LOGGER.info("patch remote test method {} success", realName);
                }
            }
        } catch (Exception e) {
            LOGGER.error("patch remote test error", e);
        }
        // LOGGER.info("patch remote test success {}", clazz.getName());
    }

    public static void remoteInfoLog(ILoggingEvent event) {
        if (event.getLevel().equals(Level.INFO)) {
            LOGGER.info(HotswapConstants.REMOTE_TEST_TAG + event.getFormattedMessage());
        } else if (event.getLevel().equals(Level.ERROR)) {
            LOGGER.error(HotswapConstants.REMOTE_TEST_TAG + event.getFormattedMessage());
        } else if (event.getLevel().equals(Level.WARN)) {
            LOGGER.warning(HotswapConstants.REMOTE_TEST_TAG + event.getFormattedMessage());
        } else if (event.getLevel().equals(Level.DEBUG)) {
            LOGGER.debug(HotswapConstants.REMOTE_TEST_TAG + event.getFormattedMessage());
        } else if (event.getLevel().equals(Level.TRACE)) {
            LOGGER.trace(HotswapConstants.REMOTE_TEST_TAG + event.getFormattedMessage());
        }
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
