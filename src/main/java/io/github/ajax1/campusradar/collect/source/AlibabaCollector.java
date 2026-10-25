package io.github.ajax1.campusradar.collect.source;

import io.github.ajax1.campusradar.collect.CollectException;
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
 * 阿里巴巴校招 campus-talent.alibaba.com。批次号每届都会变，先调 listBatch 拿到当前的应届生和实习批次。
 */
@Component
public class AlibabaCollector implements JobCollector {

    static final String BASE = "https://campus-talent.alibaba.com";
    static final int PAGE_SIZE = 50;
    static final int MAX_PAGES = 40;

    record Batch(String id, String name, RecruitType type) {
    }

    @Override
    public String source() {
        return "alibaba";
    }

    @Override
    public String company() {
        return "阿里巴巴";
    }

    @Override
    public List<String> aliases() {
        return List.of("阿里", "Alibaba", "淘天", "淘宝", "天猫", "阿里云", "通义", "千问");
    }

    @Override
    public List<RawJob> collect(HttpFetcher http) {
        // 打开列表页，拿到 XSRF-TOKEN cookie
        http.get(BASE + "/campus/position", Map.of());
        String csrf = http.cookie(BASE, "XSRF-TOKEN");
        if (csrf == null) {
            throw new CollectException("alibaba 未拿到 XSRF-TOKEN");
        }
        Map<String, String> headers = Map.of("Referer", BASE + "/campus/position");
        JsonNode batchResp = Json.parse(http.postJson(BASE + "/searchCondition/listBatch?_csrf=" + csrf,
                Json.write(Map.of("channel", "campus_group_official_site", "language", "zh")), headers));
        Json.require(batchResp.path("success").asBoolean(false), source(), batchResp);

        List<RawJob> out = new ArrayList<>();
        for (Batch batch : parseBatches(batchResp)) {
            long got = 0;
            for (int page = 1; page <= MAX_PAGES; page++) {
                JsonNode root = Json.parse(http.postJson(BASE + "/position/search?_csrf=" + csrf,
                        Json.write(searchBody(batch.id(), page)), headers));
                Json.require(root.path("success").asBoolean(false), source(), root);
                List<RawJob> items = parse(root, batch);
                out.addAll(items);
                got += items.size();
                long total = Json.number(root.path("content"), "totalCount");
                if (items.isEmpty() || got >= total) {
                    break;
                }
            }
        }
        return out;
    }

    static List<Batch> parseBatches(JsonNode root) {
        List<Batch> out = new ArrayList<>();
        JsonNode content = root.path("content");
        for (JsonNode b : content.path("graduate")) {
            out.add(new Batch(Json.text(b, "id"), Json.text(b, "name"), RecruitType.CAMPUS));
        }
        for (JsonNode b : content.path("internship")) {
            out.add(new Batch(Json.text(b, "id"), Json.text(b, "name"), RecruitType.INTERN));
        }
        return out.stream().filter(b -> b.id() != null).distinct().toList();
    }

    static Map<String, Object> searchBody(String batchId, int page) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("channel", "campus_group_official_site");
        body.put("language", "zh");
        body.put("batchId", batchId);
        body.put("categories", "");
        body.put("deptCodes", List.of());
        body.put("key", "");
        body.put("pageIndex", page);
        body.put("pageSize", PAGE_SIZE);
        body.put("regions", "");
        body.put("subCategories", "");
        return body;
    }

    static List<RawJob> parse(JsonNode root, Batch batch) {
        List<RawJob> out = new ArrayList<>();
        for (JsonNode p : root.path("content").path("datas")) {
            String id = Json.text(p, "id");
            String title = Json.text(p, "name");
            out.add(new RawJob(id, title, Json.text(p, "categoryName"), batch.type(),
                    Texts.gradYear(Json.text(p, "batchName"), batch.name(), title),
                    Texts.cities(Json.texts(p.path("workLocations"), null)),
                    Json.text(p, "description"), Json.text(p, "requirement"),
                    BASE + "/campus/position/" + id, Json.epochMillis(p, "modifyTime")));
        }
        return out;
    }
}
