package io.github.ajax1.campusradar.collect.source;

import io.github.ajax1.campusradar.collect.HttpFetcher;
import io.github.ajax1.campusradar.collect.JobCollector;
import io.github.ajax1.campusradar.collect.Json;
import io.github.ajax1.campusradar.collect.RawJob;
import io.github.ajax1.campusradar.collect.Texts;
import io.github.ajax1.campusradar.job.RecruitType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 美团校招 zhaopin.meituan.com。jobType 1 为校招，2 为实习。
 */
@Component
public class MeituanCollector implements JobCollector {

    static final String LIST_URL = "https://zhaopin.meituan.com/api/official/job/getJobList";
    static final int PAGE_SIZE = 50;
    static final int MAX_PAGES = 60;

    @Override
    public String source() {
        return "meituan";
    }

    @Override
    public String company() {
        return "美团";
    }

    @Override
    public List<String> aliases() {
        return List.of("Meituan");
    }

    @Override
    public List<RawJob> collect(HttpFetcher http) {
        List<RawJob> out = new ArrayList<>();
        for (String jobType : List.of("1", "2")) {
            RecruitType type = "2".equals(jobType) ? RecruitType.INTERN : RecruitType.CAMPUS;
            long got = 0;
            for (int page = 1; page <= MAX_PAGES; page++) {
                JsonNode root = Json.parse(http.postJson(LIST_URL, Json.write(body(jobType, page)),
                        Map.of("Referer", "https://zhaopin.meituan.com/web/campus")));
                Json.require(root.path("status").asInt(-1) == 1 || root.path("data").has("list"), source(), root);
                List<RawJob> items = parse(root, type);
                out.addAll(items);
                got += items.size();
                long total = Json.number(root.path("data").path("page"), "totalCount");
                if (items.isEmpty() || got >= total) {
                    break;
                }
            }
        }
        return out;
    }

    static Map<String, Object> body(String jobType, int page) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("page", Map.of("pageNo", page, "pageSize", PAGE_SIZE));
        body.put("jobShareType", "1");
        body.put("keywords", "");
        body.put("cityList", List.of());
        body.put("department", List.of());
        body.put("jfJgList", List.of());
        body.put("jobType", List.of(Map.of("code", jobType, "subCode", List.of())));
        body.put("typeCode", List.of());
        body.put("specialCode", List.of());
        return body;
    }

    static List<RawJob> parse(JsonNode root, RecruitType type) {
        List<RawJob> out = new ArrayList<>();
        for (JsonNode p : root.path("data").path("list")) {
            String id = Json.text(p, "jobUnionId");
            String title = Json.text(p, "name");
            out.add(new RawJob(id, title, Json.text(p, "jobFamily"), type,
                    Texts.gradYear(title, Json.text(p, "jobRequirement")),
                    Texts.cities(Json.texts(p.path("cityList"), "name")),
                    Json.text(p, "jobDuty"), Json.text(p, "jobRequirement"),
                    "https://zhaopin.meituan.com/web/position/detail?jobUnionId=" + id + "&highlightType=campus",
                    Json.epochMillis(p, "refreshTime")));
        }
        return out;
    }
}
