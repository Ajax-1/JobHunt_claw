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
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 腾讯校招 join.qq.com。列表接口不带 JD，需要按 postId 逐个拉详情。
 */
@Component
public class TencentCollector implements JobCollector {

    static final String LIST_URL = "https://join.qq.com/api/v1/position/searchPosition";
    static final String DETAIL_URL = "https://join.qq.com/api/v1/jobDetails/getJobDetailsByPostId";
    static final int PAGE_SIZE = 100;
    static final int MAX_PAGES = 50;

    @Override
    public String source() {
        return "tencent";
    }

    @Override
    public String company() {
        return "腾讯";
    }

    @Override
    public List<String> aliases() {
        return List.of("Tencent", "鹅厂", "微信", "WXG", "IEG", "PCG", "CSIG", "TEG", "CDG");
    }

    @Override
    public List<RawJob> collect(HttpFetcher http) {
        List<RawJob> out = new ArrayList<>();
        long got = 0;
        for (int page = 1; page <= MAX_PAGES; page++) {
            JsonNode root = Json.parse(http.postJson(LIST_URL, Json.write(listBody(page)),
                    Map.of("Referer", "https://join.qq.com/post.html")));
            Json.require(root.path("status").asInt(-1) == 0, source(), root);
            List<RawJob> items = parseList(root);
            out.addAll(items);
            got += items.size();
            long total = Json.number(root.path("data"), "count");
            if (items.isEmpty() || got >= total) {
                break;
            }
        }
        return out;
    }

    @Override
    public boolean needsDetail() {
        return true;
    }

    @Override
    public RawJob fetchDetail(RawJob summary, HttpFetcher http) {
        String url = DETAIL_URL + "?timestamp=" + System.currentTimeMillis() + "&postId=" + summary.externalId();
        JsonNode root = Json.parse(http.get(url, Map.of("Referer", summary.applyUrl())));
        // 列表里还挂着、详情已下架的岗位，约占一成
        if (root.path("status").asInt(-1) == 404) {
            return null;
        }
        Json.require(root.path("status").asInt(-1) == 0, source(), root);
        return parseDetail(summary, root.path("data"));
    }

    static Map<String, Object> listBody(int page) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("projectIdList", List.of());
        body.put("projectMappingIdList", List.of());
        body.put("keyword", "");
        body.put("bgList", List.of());
        body.put("workCountryType", 0);
        body.put("workCityList", List.of());
        body.put("recruitCityList", List.of());
        body.put("positionFidList", List.of());
        body.put("pageIndex", page);
        body.put("pageSize", PAGE_SIZE);
        return body;
    }

    static List<RawJob> parseList(JsonNode root) {
        List<RawJob> out = new ArrayList<>();
        for (JsonNode p : root.path("data").path("positionList")) {
            String postId = Json.text(p, "postId");
            String project = Json.text(p, "projectName");
            String cities = Json.text(p, "workCities");
            out.add(new RawJob(postId, Json.text(p, "positionTitle"), null, recruitType(project),
                    Texts.gradYear(project, Json.text(p, "positionTitle")),
                    Texts.cities(cities == null ? List.of() : Arrays.asList(cities.split("\\s+"))),
                    null, null, "https://join.qq.com/post_detail.html?postid=" + postId, null));
        }
        return out;
    }

    /**
     * 普通岗位的 JD 在 desc / request 里；青云计划的研究课题放在 topicDetail / topicRequirement 里
     */
    static RawJob parseDetail(RawJob summary, JsonNode d) {
        String description = firstNonNull(Json.text(d, "desc"), Json.text(d, "topicDetail"));
        String requirement = firstNonNull(Json.text(d, "request"), Json.text(d, "topicRequirement"));
        String bonus = firstNonNull(Json.text(d, "graduateBonus"), Json.text(d, "internBonus"));
        if (bonus != null) {
            requirement = (requirement == null ? "" : requirement + "\n\n") + "加分项：\n" + bonus;
        }
        String category = d.path("isQingyun").asInt(0) == 1
                ? firstNonNull(Json.text(d, "techTagName"), Json.text(d, "tidName"))
                : Json.text(d, "tidName");
        return summary.withDetail(category, Texts.cities(Json.texts(d.path("workCityList"), null)),
                description, requirement);
    }

    private static String firstNonNull(String a, String b) {
        return a != null ? a : b;
    }

    /**
     * 项目名如「应届毕业生」「实习生」「青云计划」
     */
    static RecruitType recruitType(String project) {
        return project != null && project.contains("实习") ? RecruitType.INTERN : RecruitType.CAMPUS;
    }
}
