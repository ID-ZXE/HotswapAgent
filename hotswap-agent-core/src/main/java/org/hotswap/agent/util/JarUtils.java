package org.hotswap.agent.util;

import org.apache.commons.io.FileUtils;
import org.hotswap.agent.javassist.bytecode.ClassFile;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.manager.AllExtensionsManager;

import java.io.*;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

public class JarUtils {

    private static final AgentLogger LOGGER = AgentLogger.getLogger(JarUtils.class);

    public static Map<String, byte[]> loadJarFile(File jarFile) {
        Map<String, byte[]> resultMap = new HashMap<>();
        try (InputStream jarStream = Files.newInputStream(jarFile.toPath()); JarInputStream jarIn = new JarInputStream(jarStream)) {
            JarEntry entry;
            while ((entry = jarIn.getNextJarEntry()) != null) {
                // 处理每个条目
                String entryName = entry.getName();
                // 根据需要进行条件判断，例如只处理.class文件
                if (entryName.endsWith(".class")) {
                    ClassFile classFile = getClassFile(jarIn);
                    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                    DataOutputStream dataOutputStream = new DataOutputStream(byteArrayOutputStream);
                    classFile.write(dataOutputStream);
                    byte[] byteArray = byteArrayOutputStream.toByteArray();

                    // 写入文件系统
                    File file = new File(AllExtensionsManager.getInstance().getExtraClassPath(),
                            classFile.getName().replace('.', '/') + ".class");
                    FileUtils.writeByteArrayToFile(file, byteArray);
                    // 写入result 后续hotswap
                    resultMap.put(classFile.getName(), byteArray);
                    LOGGER.info("读取Jar{}中的class,并写入{}到extClasspath", jarFile.getName(), classFile.getName());
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return resultMap;
    }

    private static ClassFile getClassFile(InputStream inputStream) {
        try {
            DataInputStream dis = new DataInputStream(inputStream);
            return new ClassFile(dis);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
