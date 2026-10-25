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
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * 拼多多校招 careers.pddglobalhr.com。列表只给岗位职责，没有单独的任职要求字段。
 */
@Component
public class PddCollector implements JobCollector {

    static final String LIST_URL = "https://careers.pddglobalhr.com/api/careers/api/recruit/position/list";
    static final int PAGE_SIZE = 10; // 传更大也只返回 10 条
    static final int MAX_PAGES = 100;

    @Override
    public String source() {
        return "pdd";
    }

    @Override
    public String company() {
        return "拼多多";
    }

    @Override
    public List<String> aliases() {
        return List.of("PDD", "Pinduoduo", "Temu");
    }

    @Override
    public List<RawJob> collect(HttpFetcher http) {
        List<RawJob> out = new ArrayList<>();
        long got = 0;
        for (int page = 1; page <= MAX_PAGES; page++) {
            JsonNode root = Json.parse(http.postJson(LIST_URL, Json.write(Map.of("page", page, "pageSize", PAGE_SIZE)),
                    Map.of("Referer", "https://careers.pddglobalhr.com/campus/grad")));
            Json.require(root.path("success").asBoolean(false), source(), root);
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
            String recruitTypeName = Json.text(p, "recruitTypeName");
            RecruitType type = (recruitTypeName != null && recruitTypeName.contains("实习"))
                    || (title != null && title.contains("实习")) ? RecruitType.INTERN : RecruitType.CAMPUS;
            String year = Json.text(p, "graduationYear");
            Integer gradYear = year != null && year.matches("\\d{4}") ? Integer.valueOf(year) : Texts.gradYear(title);
            out.add(new RawJob(id, title, Json.text(p, "jobName"), type, gradYear,
                    Texts.cities(Stream.ofNullable(Json.text(p, "workLocationName")).toList()),
                    Json.text(p, "jobDuty"), Json.text(p, "jobRequire"),
                    "https://careers.pddglobalhr.com/campus/grad/detail?positionId=" + id,
                    Json.epochMillis(p, "releaseTime")));
        }
        return out;
    }
}
