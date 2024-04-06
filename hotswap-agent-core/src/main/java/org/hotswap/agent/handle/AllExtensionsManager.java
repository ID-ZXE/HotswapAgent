package org.hotswap.agent.handle;

public class AllExtensionsManager {

    private static ClassLoader classLoader;

    public static void setClassLoader(ClassLoader classLoader) {
        AllExtensionsManager.classLoader = classLoader;
    }

    public static ClassLoader getClassLoader() {
        return AllExtensionsManager.classLoader;
    }

}
