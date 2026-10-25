package io.github.ajax1.campusradar.collect.source;

import io.github.ajax1.campusradar.collect.HttpFetcher;
import io.github.ajax1.campusradar.collect.JobCollector;
import io.github.ajax1.campusradar.collect.Json;
import io.github.ajax1.campusradar.collect.RawJob;
import io.github.ajax1.campusradar.collect.Texts;
import io.github.ajax1.campusradar.job.RecruitType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 百度校招 talent.baidu.com。岗位名形如「北京-网络研发工程师(J100700)」，入库前去掉城市前缀和编号。
 */
@Component
public class BaiduCollector implements JobCollector {

    static final String LIST_URL = "https://talent.baidu.com/httservice/getPostListNew";
    static final int PAGE_SIZE = 20; // 超过 20 会返回 Illegal argument : pageSize
    static final int MAX_PAGES = 100;
    private static final Pattern CODE_SUFFIX = Pattern.compile("\\s*[(（][A-Z]?\\d+[)）]\\s*$");
    private static final ZoneId CHINA = ZoneId.of("Asia/Shanghai");

    @Override
    public String source() {
        return "baidu";
    }

    @Override
    public String company() {
        return "百度";
    }

    @Override
    public List<String> aliases() {
        return List.of("Baidu");
    }

    @Override
    public List<RawJob> collect(HttpFetcher http) {
        List<RawJob> out = new ArrayList<>();
        for (String recruitType : List.of("GRADUATE", "INTERN")) {
            long got = 0;
            for (int page = 1; page <= MAX_PAGES; page++) {
                Map<String, String> form = new LinkedHashMap<>();
                form.put("recruitType", recruitType);
                form.put("pageSize", String.valueOf(PAGE_SIZE));
                form.put("keyWord", "");
                form.put("curPage", String.valueOf(page));
                form.put("projectType", "");
                JsonNode root = Json.parse(http.postForm(LIST_URL, form, Map.of("Referer", "https://talent.baidu.com/jobs/list")));
                Json.require("ok".equals(Json.text(root, "status")), source(), root);
                List<RawJob> items = parse(root, recruitType);
                out.addAll(items);
                got += items.size();
                long total = Json.number(root.path("data"), "total");
                if (items.isEmpty() || got >= total) {
                    break;
                }
            }
        }
        return out;
    }

    static List<RawJob> parse(JsonNode root, String recruitType) {
        List<RawJob> out = new ArrayList<>();
        RecruitType type = "INTERN".equals(recruitType) ? RecruitType.INTERN : RecruitType.CAMPUS;
        for (JsonNode p : root.path("data").path("list")) {
            String postId = Json.text(p, "postId");
            String rawName = Json.text(p, "name");
            List<String> cities = Texts.cities(Stream.ofNullable(Json.text(p, "workPlace")).toList());
            out.add(new RawJob(postId, cleanTitle(rawName, cities), Json.text(p, "postType"), type,
                    Texts.gradYear(rawName, Json.text(p, "projectType")), cities,
                    Json.text(p, "workContent"), Json.text(p, "serviceCondition"),
                    "https://talent.baidu.com/jobs/detail/" + recruitType + "/" + postId,
                    date(Json.text(p, "publishDate"))));
        }
        return out;
    }

    static String cleanTitle(String name, List<String> cities) {
        if (name == null) {
            return null;
        }
        String t = CODE_SUFFIX.matcher(name).replaceFirst("");
        int dash = t.indexOf('-');
        if (dash > 0 && dash < t.length() - 1) {
            String prefix = t.substring(0, dash);
            if (cities.contains(prefix) || Texts.cities(List.of(prefix)).stream().anyMatch(cities::contains)) {
                t = t.substring(dash + 1);
            }
        }
        return t.trim();
    }

    private static java.time.Instant date(String s) {
        if (s == null) {
            return null;
        }
        try {
            return LocalDate.parse(s).atStartOfDay(CHINA).toInstant();
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
