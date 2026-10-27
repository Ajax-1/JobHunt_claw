package io.github.ajax1.campusradar.match;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * 关键词命中判断。中文直接按子串匹配；英文、数字类关键词要求两侧不是字母或数字，
 * 避免 java 命中 javascript、go 命中 google。常见同义写法（Go / Golang、K8s / Kubernetes）互相算命中。
 */
public final class Terms {

    private static final Map<String, List<String>> SYNONYMS = Map.ofEntries(
            Map.entry("go", List.of("golang", "go语言")),
            Map.entry("golang", List.of("go", "go语言")),
            Map.entry("javascript", List.of("js")),
            Map.entry("js", List.of("javascript")),
            Map.entry("typescript", List.of("ts")),
            Map.entry("k8s", List.of("kubernetes")),
            Map.entry("kubernetes", List.of("k8s")),
            Map.entry("c++", List.of("cpp")),
            Map.entry("cpp", List.of("c++")),
            Map.entry("postgresql", List.of("postgres", "pg")),
            Map.entry("llm", List.of("大模型")),
            Map.entry("大模型", List.of("llm")),
            Map.entry("机器学习", List.of("machine learning")),
            Map.entry("深度学习", List.of("deep learning")));

    private Terms() {
    }

    /**
     * 关键词本身加上同义写法，小写
     */
    public static List<String> variants(String term) {
        String t = term.trim().toLowerCase(Locale.ROOT);
        return Stream.concat(Stream.of(t), SYNONYMS.getOrDefault(t, List.of()).stream()).distinct().toList();
    }

    /**
     * @param lowerText 已转小写的文本
     * @param term      关键词，大小写不限
     */
    public static boolean contains(String lowerText, String term) {
        if (lowerText == null || lowerText.isEmpty() || term == null || term.isBlank()) {
            return false;
        }
        return variants(term).stream().anyMatch(variant -> containsOne(lowerText, variant));
    }

    private static boolean containsOne(String text, String term) {
        if (!hasAsciiWordChar(term)) {
            return text.contains(term);
        }
        int from = 0;
        while (true) {
            int at = text.indexOf(term, from);
            if (at < 0) {
                return false;
            }
            int end = at + term.length();
            boolean leftOk = at == 0 || !isAsciiWordChar(text.charAt(at - 1)) || !isAsciiWordChar(term.charAt(0));
            boolean rightOk = end == text.length() || !isAsciiWordChar(text.charAt(end))
                    || !isAsciiWordChar(term.charAt(term.length() - 1));
            if (leftOk && rightOk) {
                return true;
            }
            from = at + 1;
        }
    }

    private static boolean hasAsciiWordChar(String s) {
        return s.chars().anyMatch(c -> isAsciiWordChar((char) c));
    }

    /**
     * 只把英文字母和数字当作「词内字符」：中文紧挨着英文（如「熟悉Java开发」）也算独立的词
     */
    private static boolean isAsciiWordChar(char c) {
        return (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9');
    }
}
