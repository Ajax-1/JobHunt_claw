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
 * 京东校招 campus.jd.com。工作城市不在岗位本身上，而在 requirementVoList 的每个招聘需求里。
 */
@Component
public class JdCollector implements JobCollector {

    static final String LIST_URL = "https://campus.jd.com/api/wx/position/page?type=present";
    static final int PAGE_SIZE = 50;
    static final int MAX_PAGES = 40;

    @Override
    public String source() {
        return "jd";
    }

    @Override
    public String company() {
        return "京东";
    }

    @Override
    public List<String> aliases() {
        return List.of("JD", "Jingdong");
    }

    @Override
    public List<RawJob> collect(HttpFetcher http) {
        List<RawJob> out = new ArrayList<>();
        long got = 0;
        // pageIndex 从 0 开始
        for (int page = 0; page < MAX_PAGES; page++) {
            JsonNode root = Json.parse(http.postJson(LIST_URL, Json.write(body(page)),
                    Map.of("Referer", "https://campus.jd.com/")));
            Json.require(root.path("success").asBoolean(false), source(), root);
            List<RawJob> items = parse(root);
            out.addAll(items);
            got += items.size();
            long total = Json.number(root.path("body"), "totalNumber");
            if (items.isEmpty() || got >= total) {
                break;
            }
        }
        return out;
    }

    static Map<String, Object> body(int page) {
        Map<String, Object> parameter = new LinkedHashMap<>();
        parameter.put("positionName", "");
        parameter.put("planIdList", List.of());
        parameter.put("jobDirectionCodeList", List.of());
        parameter.put("workCityCodeList", List.of());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("pageSize", PAGE_SIZE);
        body.put("pageIndex", page);
        body.put("parameter", parameter);
        return body;
    }

    static List<RawJob> parse(JsonNode root) {
        List<RawJob> out = new ArrayList<>();
        for (JsonNode p : root.path("body").path("items")) {
            String id = Json.text(p, "publishId");
            String title = Json.text(p, "positionName");
            RecruitType type = title != null && title.contains("实习") ? RecruitType.INTERN : RecruitType.CAMPUS;
            out.add(new RawJob(id, title, Json.text(p, "jobCategory"), type,
                    Texts.gradYear(title, Json.text(p, "qualification")),
                    Texts.cities(Json.texts(p.path("requirementVoList"), "workCity")),
                    Json.text(p, "workContent"), Json.text(p, "qualification"),
                    "https://campus.jd.com/#/details?id=" + id, Json.epochMillis(p, "publishTime")));
        }
        return out;
    }
}
