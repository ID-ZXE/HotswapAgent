package org.hotswap.agent.util;

import cn.hutool.http.HttpUtil;
import org.hotswap.agent.dto.BaseResponse;
import org.hotswap.agent.logging.AgentLogger;

import java.io.File;
import java.net.URL;
import java.util.Map;

public final class HttpUtils {

    private static final AgentLogger LOGGER = AgentLogger.getLogger(HttpUtils.class);

    public static BaseResponse<?> get(String url, Map<String, Object> params) {
        try {
            String result = HttpUtil.get(url, params);
            return JsonUtils.toObject(result, BaseResponse.class);
        } catch (Exception e) {
            throw new RuntimeException("request " + url + " error", e);
        }
    }

    public static void downloadFile(String url, String dir) {
        try {
            String fileName = new URL(url).getFile().substring(new URL(url).getFile().lastIndexOf('/') + 1);
            File file = new File(dir, fileName);
            HttpUtil.downloadFile(url, file);
            LOGGER.info("文件下载成功，保存在:{}", file.getAbsolutePath());
        } catch (Exception e) {
            throw new RuntimeException("文件下载失败", e);
        }
    }

}
