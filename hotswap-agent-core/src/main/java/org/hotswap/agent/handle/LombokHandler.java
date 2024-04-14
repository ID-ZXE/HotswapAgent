package org.hotswap.agent.handle;

import org.apache.commons.io.FileUtils;
import org.hotswap.agent.constants.HotswapConstants;
import org.hotswap.agent.logging.AgentLogger;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;

public class LombokHandler {

    private static final AgentLogger LOGGER = AgentLogger.getLogger(LombokHandler.class);

    private static final String DE_LOMBOK_COMMAND = "java -jar %s delombok -p %s";


    public static void deLombok(List<File> javaFileList) {
        long start = System.currentTimeMillis();
        CountDownLatch latch = new CountDownLatch(javaFileList.size());
        for (File javaFile : javaFileList) {
            new Thread(() -> {
                try {
                    byte[] bytes = Files.readAllBytes(javaFile.toPath());
                    String content = new String(bytes, StandardCharsets.UTF_8);
                    if (content.contains("lombok")) {
                        return;
                    }

                    File jar = new File(HotswapConstants.EXT_CLASS_PATH, "lombok.jar");
                    String command = String.format(DE_LOMBOK_COMMAND, jar.getAbsolutePath(), javaFile.getAbsolutePath());
                    LOGGER.info("execute command:{}", command);
                    Process process = Runtime.getRuntime().exec(command);
                    int exitCode = process.waitFor();
                    if (!Objects.equals(exitCode, 0)) {
                        throw new RuntimeException("execute deLombok command exit code is not 1");
                    }
                    BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                    List<String> lines = new ArrayList<>();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        lines.add(line);
                    }
                    FileUtils.writeLines(javaFile, lines, false);
                    LOGGER.info("deLombok {} success", javaFile.getName());
                } catch (Exception e) {
                    LOGGER.error("deLombok {} error", e, javaFile);
                } finally {
                    latch.countDown();
                }
            }).start();
        }
        try {
            latch.await();
        } catch (InterruptedException e) {
            LOGGER.error("deLombok await error", e);
        }
        LOGGER.info("deLombok cost:{}", System.currentTimeMillis() - start);
    }

}
