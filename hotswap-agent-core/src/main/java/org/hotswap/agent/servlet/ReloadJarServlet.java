package org.hotswap.agent.servlet;


import org.hotswap.agent.HotswapApplication;
import org.hotswap.agent.dto.ReloadResultDTO;
import org.hotswap.agent.handle.CompileEngine;
import org.hotswap.agent.manager.AllExtensionsManager;
import org.hotswap.agent.util.JarUtils;
import org.hotswap.agent.util.spring.util.StringUtils;

import javax.servlet.MultipartConfigElement;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.Part;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

@MultipartConfig
@WebServlet(urlPatterns = "/reloadJar", name = "reloadJar")
public class ReloadJarServlet extends AbstractHttpServlet {

    public static final String MULTIPART_CONFIG_ELEMENT = "org.eclipse.jetty.multipartConfig";

    private static final MultipartConfigElement MULTI_PART_CONFIG = new MultipartConfigElement("/tmp");

    @Override
    @SuppressWarnings("all")
    public synchronized Object doExecute() throws Exception {
        long start = System.currentTimeMillis();
        ReloadResultDTO reloadResultDTO = new ReloadResultDTO();
        CompileEngine.getInstance().setIsCompiling(true);
        try {
            req.setAttribute(MULTIPART_CONFIG_ELEMENT, MULTI_PART_CONFIG);
            Part filePart = req.getPart("jar");
            if (filePart == null) {
                throw new RuntimeException("未找到上传的文件");
            }
            String fileName = extractFileName(filePart);
            InputStream inputStream = filePart.getInputStream();
            if (StringUtils.isEmpty(fileName)) {
                throw new RuntimeException("文件名获取失败");
            }
            writeFile(inputStream, fileName);
            File jarFile = new File(AllExtensionsManager.getInstance().getJarDirPath(), fileName);
            Map<String, byte[]> clazzMap = JarUtils.loadJarFile(jarFile);

            Map<Class<?>, byte[]> reloadMap = new HashMap<>();
            for (Map.Entry<String, byte[]> entry : clazzMap.entrySet()) {
                Class<?> clazz = AllExtensionsManager.getInstance().getClassLoader().loadClass(entry.getKey());
                reloadMap.put(clazz, entry.getValue());
            }
            CompileEngine.getInstance().setCompileResult(reloadMap);
            long reloadCostTime = HotswapApplication.getInstance().openChannel();
            reloadResultDTO.setTotalCostTime(System.currentTimeMillis() - start);
            reloadResultDTO.setReloadCostTime(reloadCostTime);
        } finally {
            CompileEngine.getInstance().setIsCompiling(false);
        }
        return reloadResultDTO;
    }

    @Override
    protected boolean isUploadFile() {
        return true;
    }

    private void writeFile(InputStream inputStream, String fileName) throws IOException {
        File jarFile = new File(AllExtensionsManager.getInstance().getJarDirPath(), fileName);
        if (jarFile.exists()) {
            jarFile.delete();
        }

        try (BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(
                Files.newOutputStream(jarFile.toPath()))) {
            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                bufferedOutputStream.write(buffer, 0, bytesRead);
            }
        } finally {
            try {
                if (inputStream != null) {
                    inputStream.close();
                }
            } catch (IOException ignore) {
            }
        }
    }

    private String extractFileName(Part part) {
        String contentDisposition = part.getHeader("Content-Disposition");
        String[] tokens = contentDisposition.split(";");
        for (String token : tokens) {
            if (token.trim().startsWith("filename")) {
                return token.substring(token.indexOf('=') + 1).replace("\"", "");
            }
        }
        return null;
    }

}
