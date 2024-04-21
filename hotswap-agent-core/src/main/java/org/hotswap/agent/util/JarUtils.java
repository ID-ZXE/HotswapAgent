package org.hotswap.agent.util;

import org.hotswap.agent.javassist.bytecode.ClassFile;

import java.io.*;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

public class JarUtils {

    public static void loadJarFile(File jarFile) {
        try (InputStream jarStream = new FileInputStream(jarFile);
             JarInputStream jarIn = new JarInputStream(jarStream)) {
            JarEntry entry;
            while ((entry = jarIn.getNextJarEntry()) != null) {
                // 处理每个条目
                String entryName = entry.getName();
                // 根据需要进行条件判断，例如只处理.class文件
                if (entryName.endsWith(".class")) {
                    // 读取条目内容
                    System.out.println(entry.getName());
                    System.out.println(readClassName(jarIn));
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static String readClassName(InputStream inputStream) {
        try {
            DataInputStream dis = new DataInputStream(inputStream);
            ClassFile classFile = new ClassFile(dis);
            return classFile.getName().replace('/', '.');
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
