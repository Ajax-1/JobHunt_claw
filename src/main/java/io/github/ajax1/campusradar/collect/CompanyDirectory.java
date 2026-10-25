package io.github.ajax1.campusradar.collect;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * 把 Agent 传进来的公司叫法（「字节」「ByteDance」「抖音」）解析成库里的标准公司名
 */
@Component
public class CompanyDirectory {

    /**
     * 归一化后的叫法 -> 标准公司名
     */
    private final Map<String, String> names = new LinkedHashMap<>();
    private final List<String> companies;

    @Autowired
    public CompanyDirectory(CollectorRegistry registry) {
        this(registry.all());
    }

    public CompanyDirectory(List<JobCollector> collectors) {
        for (JobCollector c : collectors) {
            names.putIfAbsent(normalize(c.company()), c.company());
            for (String alias : c.aliases()) {
                names.putIfAbsent(normalize(alias), c.company());
            }
        }
        this.companies = collectors.stream().map(JobCollector::company).distinct().toList();
    }

    public List<String> companies() {
        return companies;
    }

    /**
     * 先按全称和别名精确匹配，再看是否互相包含（如「字节跳动校招」「阿里巴巴集团」）
     */
    public Optional<String> resolve(String input) {
        if (input == null || input.isBlank()) {
            return Optional.empty();
        }
        String key = normalize(input);
        String exact = names.get(key);
        if (exact != null) {
            return Optional.of(exact);
        }
        List<String> fuzzy = names.entrySet().stream()
                .filter(e -> fuzzyEligible(e.getKey()) && fuzzyEligible(key)
                        && (key.contains(e.getKey()) || e.getKey().contains(key)))
                .map(Map.Entry::getValue)
                .distinct()
                .toList();
        return fuzzy.size() == 1 ? Optional.of(fuzzy.getFirst()) : Optional.empty();
    }

    /**
     * 太短的叫法不做包含匹配：中文至少 2 个字，纯英文至少 4 个字母（避免 nio、jd 误中别的词）
     */
    private static boolean fuzzyEligible(String s) {
        return s.chars().allMatch(ch -> ch < 128) ? s.length() >= 4 : s.length() >= 2;
    }

    private static String normalize(String s) {
        return s.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }
}
