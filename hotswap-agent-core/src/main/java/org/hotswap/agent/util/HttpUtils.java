package org.hotswap.agent.util;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.file.Files;

public final class HttpUtils {

    public static void downloadUrl(String url, String dir) {
        // 创建OkHttpClient实例
        OkHttpClient client = new OkHttpClient();
        // 构建Request对象
        Request request = new Request.Builder().url(url).build();
        try {
            String fileName = new URL(url).getFile().substring(url.lastIndexOf('/') + 1);
            Response response = client.newCall(request).execute();
            if (response.isSuccessful()) {
                InputStream inputStream = response.body().byteStream();
                OutputStream outputStream = Files.newOutputStream(new File(dir, fileName).toPath());

                // 缓冲区
                byte[] buffer = new byte[4096];
                int bytesRead;

                // 读取输入流并写入文件，直到所有数据都被写入
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
                // 完成写入并关闭流
                outputStream.flush();
                outputStream.close();
                inputStream.close();
            }
        } catch (Exception e) {
            throw new RuntimeException("文件下载失败:" + url, e);
        }
    }

}