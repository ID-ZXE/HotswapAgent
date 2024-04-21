package org.hotswap.agent.servlet;


import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.manager.AllExtensionsManager;
import org.hotswap.agent.util.spring.util.StringUtils;

import javax.servlet.MultipartConfigElement;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.Part;
import java.io.*;

@MultipartConfig
@WebServlet(urlPatterns = "/reloadJar", name = "reloadJar")
public class ReloadJarServlet extends AbstractHttpServlet {

    private static final AgentLogger LOGGER = AgentLogger.getLogger(ReloadJarServlet.class);

    public static final String MULTIPART_CONFIG_ELEMENT = "org.eclipse.jetty.multipartConfig";

    private static final MultipartConfigElement MULTI_PART_CONFIG = new MultipartConfigElement("/tmp");

    @Override
    @SuppressWarnings("all")
    public synchronized Object doExecute() throws Exception {
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

        File jarFile = new File(AllExtensionsManager.getInstance().getJarDirPath(), fileName);
        if (jarFile.exists()) {
            jarFile.delete();
        }

        try (BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(new FileOutputStream(jarFile))) {
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
        return null;
    }

    @Override
    protected boolean isUploadFile() {
        return true;
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
