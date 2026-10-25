package io.github.ajax1.campusradar.collect;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 解析各家接口时用到的取值工具：缺字段、null、空串一律当作 null
 */
public final class Json {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private Json() {
    }

    public static JsonNode parse(String body) {
        return MAPPER.readTree(body);
    }

    public static String write(Map<String, ?> value) {
        return MAPPER.writeValueAsString(value);
    }

    public static String text(JsonNode node, String field) {
        JsonNode v = node.path(field);
        if (v.isMissingNode() || v.isNull()) {
            return null;
        }
        String s = v.isValueNode() ? v.asString() : v.toString();
        return s == null || s.isBlank() ? null : s.trim();
    }

    /**
     * 毫秒时间戳，兼容数字和字符串两种写法
     */
    public static Instant epochMillis(JsonNode node, String field) {
        String s = text(node, field);
        if (s == null) {
            return null;
        }
        try {
            long ms = Long.parseLong(s);
            return ms > 0 ? Instant.ofEpochMilli(ms) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static long number(JsonNode node, String field) {
        String s = text(node, field);
        if (s == null) {
            return 0;
        }
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * 从对象数组里取出每个元素的某个字段，如 cityList[].name
     */
    public static List<String> texts(JsonNode array, String field) {
        List<String> out = new ArrayList<>();
        for (JsonNode item : array) {
            String s = field == null ? (item.isValueNode() ? item.asString() : null) : text(item, field);
            if (s != null && !s.isBlank()) {
                out.add(s.trim());
            }
        }
        return out;
    }

    /**
     * 业务码校验：各家都是 HTTP 200 + body 里的 code 表示失败
     */
    public static void require(boolean ok, String source, JsonNode root) {
        if (!ok) {
            String s = root.toString();
            throw new CollectException(source + " 接口返回异常: " + (s.length() > 200 ? s.substring(0, 200) : s));
        }
    }
}
