package com.git.hui.jobclaw.core.utils.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.Getter;
import org.hibernate.Hibernate;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.List;

/**
 * @date 2025/7/14
 */
public class JsonUtil {
    @Getter
    private static ObjectMapper mapper = new ObjectMapper();

    static {
        mapper.findAndRegisterModules();
        // 显式注册Java 8时间模块,支持LocalDateTime等类型的序列化/反序列化
        mapper.registerModule(new JavaTimeModule());
        SimpleModule module = new SimpleModule();
        mapper.registerModule(module);
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        
        // 允许解析包含未转义控制字符(如Tab、换行符)的JSON字符串
        // 解决大模型返回数据中包含制表符等控制字符导致的解析失败问题
        mapper.configure(JsonParser.Feature.ALLOW_UNQUOTED_CONTROL_CHARS, true);
    }


    /**
     * 对象转字符串
     *
     * @param o 对象
     * @return 字符串
     */
    public static String toStr(Object o) {
        try {
            // 在使用JPA时，Hibernate会创建代理对象来实现延迟加载等功能，这会导致获取到的对象是HibernateProxy代理对象
            // 为了避免这种代理对象的序列化异常，我们做一个代理对象转换成实体对象的动作
            if (o.getClass().getName().contains("HibernateProxy")) {
                o = Hibernate.unproxy(o);
            }
            return mapper.writeValueAsString(o);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static <T> List<T> toList(String s, Class<T> clazz) {
        try {
            return mapper.readValue(s, mapper.getTypeFactory().constructCollectionType(List.class, clazz));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static <T> T toObj(String s, Class<T> clazz) {
        try {
            return mapper.readValue(s, clazz);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> T toObj(String s, Type type) {
        try {
            return mapper.readValue(s, new TypeReference<>() {
                @Override
                public Type getType() {
                    return type;
                }
            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static <T> T toObj(String s, TypeReference<T> typeReference) {
        try {
            return mapper.readValue(s, typeReference);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static JsonNode toJsonNode(String s) {
        try {
            return mapper.readTree(s);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
