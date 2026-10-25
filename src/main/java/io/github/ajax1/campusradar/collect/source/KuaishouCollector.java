package io.github.ajax1.campusradar.collect.source;

import io.github.ajax1.campusradar.collect.HttpFetcher;
import io.github.ajax1.campusradar.collect.JobCollector;
import io.github.ajax1.campusradar.collect.Json;
import io.github.ajax1.campusradar.collect.RawJob;
import io.github.ajax1.campusradar.collect.Texts;
import io.github.ajax1.campusradar.job.RecruitType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 快手校招 campus.kuaishou.cn。recruitSubProjectCodes 传空数组即返回所有校招项目的岗位，不必知道当届项目编码。
 */
@Component
public class KuaishouCollector implements JobCollector {

    static final String LIST_URL = "https://campus.kuaishou.cn/recruit/campus/e/api/v1/open/positions/simple";
    static final int PAGE_SIZE = 100;
    static final int MAX_PAGES = 30;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final ZoneId CHINA = ZoneId.of("Asia/Shanghai");

    @Override
    public String source() {
        return "kuaishou";
    }

    @Override
    public String company() {
        return "快手";
    }

    @Override
    public List<String> aliases() {
        return List.of("Kuaishou");
    }

    @Override
    public List<RawJob> collect(HttpFetcher http) {
        List<RawJob> out = new ArrayList<>();
        long got = 0;
        for (int page = 1; page <= MAX_PAGES; page++) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("pageNum", page);
            body.put("pageSize", PAGE_SIZE);
            body.put("recruitSubProjectCodes", List.of());
            JsonNode root = Json.parse(http.postJson(LIST_URL, Json.write(body),
                    Map.of("Referer", "https://campus.kuaishou.cn/recruit/campus/e/")));
            Json.require(root.path("code").asInt(-1) == 0, source(), root);
            List<RawJob> items = parse(root);
            out.addAll(items);
            got += items.size();
            long total = Json.number(root.path("result"), "total");
            if (items.isEmpty() || got >= total) {
                break;
            }
        }
        return out;
    }

    static List<RawJob> parse(JsonNode root) {
        List<RawJob> out = new ArrayList<>();
        for (JsonNode p : root.path("result").path("list")) {
            String id = Json.text(p, "id");
            String title = Json.text(p, "name");
            String nature = Json.text(p, "positionNatureCode");
            RecruitType type = (nature != null && nature.toLowerCase().contains("intern"))
                    || (title != null && title.contains("实习")) ? RecruitType.INTERN : RecruitType.CAMPUS;
            out.add(new RawJob(id, title, null, type, Texts.gradYear(title, Json.text(p, "positionDemand")),
                    Texts.cities(Json.texts(p.path("workLocationDicts"), "name")),
                    Json.text(p, "description"), Json.text(p, "positionDemand"),
                    "https://campus.kuaishou.cn/recruit/campus/e/#/campus/job-info/" + id,
                    time(Json.text(p, "releaseTime"))));
        }
        return out;
    }

    private static java.time.Instant time(String s) {
        if (s == null) {
            return null;
        }
        try {
            return LocalDateTime.parse(s, TIME).atZone(CHINA).toInstant();
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
