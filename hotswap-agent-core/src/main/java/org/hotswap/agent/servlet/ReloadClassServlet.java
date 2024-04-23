package org.hotswap.agent.servlet;

import org.apache.commons.io.FileUtils;
import org.hotswap.agent.HotswapApplication;
import org.hotswap.agent.dto.ContentDTO;
import org.hotswap.agent.dto.ReloadResultDTO;
import org.hotswap.agent.handle.CompileEngine;
import org.hotswap.agent.javassist.bytecode.ClassFile;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.manager.AllExtensionsManager;
import org.hotswap.agent.util.JsonUtils;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ReloadClassServlet extends AbstractHttpServlet {

    private static final AgentLogger LOGGER = AgentLogger.getLogger(ReloadClassServlet.class);

    @Override
    public Object doExecute() throws Exception {
        long start = System.currentTimeMillis();
        ReloadResultDTO reloadResultDTO = new ReloadResultDTO();
        CompileEngine.getInstance().setIsCompiling(true);
        Map<Class<?>, byte[]> reloadMap = new HashMap<>();
        Map<String, byte[]> classNameMap = new HashMap<>();
        try {
            ContentDTO contentDTO = JsonUtils.toObject(body, ContentDTO.class);
            List<byte[]> classByteList = contentDTO.getClasses();
            for (byte[] classByte : classByteList) {
                ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(classByte);
                DataInputStream dataInputStream = new DataInputStream(byteArrayInputStream);
                ClassFile classFile = new ClassFile(dataInputStream);

                // 写入文件系统
                File file = new File(AllExtensionsManager.getInstance().getExtraClassPath(),
                        classFile.getName().replace('.', '/') + ".class");
                LOGGER.info("读取、写入{}到extClasspath", classFile.getName());
                FileUtils.writeByteArrayToFile(file, classByte);
                classNameMap.put(classFile.getName(), classByte);
            }

            for (Map.Entry<String, byte[]> entry : classNameMap.entrySet()) {
                Class<?> clazz = AllExtensionsManager.getInstance().getClassLoader().loadClass(entry.getKey());
                reloadMap.put(clazz, entry.getValue());
            }

            CompileEngine.getInstance().setCompileResult(reloadMap);
            long reloadCostTime = HotswapApplication.getInstance().openChannel();
            reloadResultDTO.setTotalCostTime(System.currentTimeMillis() - start);
            reloadResultDTO.setReloadCostTime(reloadCostTime);
            reloadResultDTO.setSuccess(true);
        } finally {
            CompileEngine.getInstance().setIsCompiling(false);
        }
        return reloadResultDTO;
    }

}
