package org.hotswap.agent.servlet;

import org.apache.commons.io.FileUtils;
import org.hotswap.agent.dto.ContentDTO;
import org.hotswap.agent.manager.AllExtensionsManager;
import org.hotswap.agent.util.JsonUtils;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;

public class UploadReloadFileServlet extends AbstractHttpServlet {

    @Override
    public Object doExecute() throws Exception {
        ContentDTO contentDTO = JsonUtils.toObject(body, ContentDTO.class);
        Map<String, String> contentMap = contentDTO.getContent();

        if (Objects.equals(contentDTO.getToClasspath(), true)) {
            for (Map.Entry<String, String> entry : contentMap.entrySet()) {
                byte[] decode = Base64.getDecoder().decode(entry.getValue());
                File file = new File(AllExtensionsManager.getInstance().getExtraClassPath(), entry.getKey());
                FileUtils.write(file, new String(decode), StandardCharsets.UTF_8, false);
            }
        } else {
            for (Map.Entry<String, String> entry : contentMap.entrySet()) {
                byte[] decode = Base64.getDecoder().decode(entry.getValue());
                File file = new File(AllExtensionsManager.getInstance().getSourceDirPath(), entry.getKey());
                FileUtils.write(file, new String(decode), StandardCharsets.UTF_8, false);
            }
        }
        return null;
    }

}
