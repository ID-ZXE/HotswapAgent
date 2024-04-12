package org.hotswap.agent.util;

import org.hotswap.agent.logging.AgentLogger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public final class XmlUtils {

    private static final AgentLogger LOGGER = AgentLogger.getLogger(XmlUtils.class);


    private XmlUtils() {
    }

    public static boolean isMyBatisXML(String xmlFilePath) {
        try {
            List<String> lines = Files.readAllLines(Paths.get(xmlFilePath));
            for (String line : lines) {
                if (line.contains("mybatis.org")) {
                    return true;
                }
            }
            return false;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static boolean isLogbackXML(String xmlFilePath) {
        try {
            List<String> lines = Files.readAllLines(Paths.get(xmlFilePath));
            for (String line : lines) {
                if (line.contains("appender")) {
                    return true;
                }
            }
            return false;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


}
