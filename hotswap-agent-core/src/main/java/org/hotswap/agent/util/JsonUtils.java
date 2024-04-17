package org.hotswap.agent.util;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.ser.FilterProvider;
import com.fasterxml.jackson.databind.ser.impl.SimpleBeanPropertyFilter;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;
import org.hotswap.agent.logging.AgentLogger;

/**
 * @author hangs.zhang
 * @date 2020/5/21 下午3:15
 * *********************
 * function:
 */
public final class JsonUtils {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final AgentLogger LOGGER = AgentLogger.getLogger(JsonUtils.class);

    private static final String JSON_ERROR_FORMAT = "Json 解析异常,Json:{}";

    private static final String JSON_ERROR_MESSAGE = "Json解析异常";

    static {
        FilterProvider theFilter = new SimpleFilterProvider().
                addFilter("outFilter", SimpleBeanPropertyFilter.serializeAllExcept());
        MAPPER.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        MAPPER.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
        // 设置输入时忽略在JSON字符串中存在但Java对象实际没有的属性
        MAPPER.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        //当bean没有属性不报错
        MAPPER.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        // 主要用于日志答应输出全部
        MAPPER.setFilterProvider(theFilter);
    }

    private JsonUtils() {
    }

    public static String getJsonNodeContent(String json, String nodeName) {
        try {
            return MAPPER.readTree(json).get(nodeName).toString();
        } catch (Exception e) {
            LOGGER.error(JSON_ERROR_FORMAT, json);
            throw new RuntimeException(JSON_ERROR_MESSAGE, e);
        }
    }

    public static String toString(Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (Exception e) {
            throw new RuntimeException(JSON_ERROR_MESSAGE, e);
        }
    }

    public static <T> T toObject(String json, Class<T> type) {
        try {
            return MAPPER.readValue(json, type);
        } catch (Exception e) {
            LOGGER.error(JSON_ERROR_FORMAT, json);
            throw new RuntimeException(JSON_ERROR_MESSAGE, e);
        }
    }

    public static <T> T toObject(String json, TypeReference<T> typeRef) {
        try {
            return MAPPER.readValue(json, typeRef);
        } catch (Exception e) {
            LOGGER.error(JSON_ERROR_FORMAT, json);
            throw new RuntimeException(JSON_ERROR_MESSAGE, e);
        }
    }

}
