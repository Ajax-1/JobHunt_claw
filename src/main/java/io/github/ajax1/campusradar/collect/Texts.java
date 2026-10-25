package io.github.ajax1.campusradar.collect;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 跨来源的字段归一化
 */
public final class Texts {

    private static final Pattern GRAD_YEAR = Pattern.compile("(20\\d{2}|(?<![0-9])\\d{2})\\s*届");
    private static final Set<String> NOT_A_CITY = Set.of("远程", "远程面试", "不限", "全国", "待定");

    private Texts() {
    }

    /**
     * 「深圳总部」「北京市-北京市」「上海市」统一成「深圳」「北京」「上海」，并去重保序
     */
    public static List<String> cities(List<String> raw) {
        Set<String> out = new LinkedHashSet<>();
        for (String r : raw) {
            if (r == null) {
                continue;
            }
            for (String part : r.split("[,，、/\\s]+")) {
                String c = part.trim();
                int dash = c.indexOf('-');
                if (dash > 0) {
                    c = c.substring(0, dash);
                }
                c = c.replace("总部", "");
                if (c.endsWith("市") && c.length() > 2) {
                    c = c.substring(0, c.length() - 1);
                }
                if (!c.isEmpty() && !NOT_A_CITY.contains(c)) {
                    out.add(c);
                }
            }
        }
        return List.copyOf(out);
    }

    /**
     * 存库格式 ",北京,上海,"；没有城市时为 null
     */
    public static String joinCities(List<String> cities) {
        return cities.isEmpty() ? null : "," + String.join(",", cities) + ",";
    }

    /**
     * 从「2027届」「27届秋招」这类文字里取毕业届别
     */
    public static Integer gradYear(String... texts) {
        for (String t : texts) {
            if (t == null) {
                continue;
            }
            Matcher m = GRAD_YEAR.matcher(t);
            if (m.find()) {
                int y = Integer.parseInt(m.group(1));
                return y < 100 ? 2000 + y : y;
            }
        }
        return null;
    }

    public static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    public static String hash(String... parts) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            String joined = String.join("\u0001", Arrays.stream(parts).map(p -> Objects.toString(p, "")).toList());
            return HexFormat.of().formatHex(md.digest(joined.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
