package io.github.ajax1.campusradar.collect.source;

import io.github.ajax1.campusradar.collect.CollectException;
import io.github.ajax1.campusradar.collect.HttpFetcher;
import io.github.ajax1.campusradar.collect.JobCollector;
import io.github.ajax1.campusradar.collect.Json;
import io.github.ajax1.campusradar.collect.RawJob;
import io.github.ajax1.campusradar.collect.Texts;
import io.github.ajax1.campusradar.job.RecruitType;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 飞书招聘（*.jobs.feishu.cn）通用适配器。字节跳动官网 jobs.bytedance.com 是同一套系统。
 * 先取 csrf token（同时写入 cookie），再带上 website-path: campus 查校招站的岗位。
 * 站点列表在 application.yml 的 radar.feishu-sites 里配置，加一家公司只需加一行。
 */
public class FeishuAtsCollector implements JobCollector {

    static final int PAGE_SIZE = 100;
    static final int MAX_PAGES = 120;

    private final String source;
    private final String company;
    private final List<String> aliases;
    private final String baseUrl;

    /**
     * @param baseUrl 站点根地址，如 https://nio.jobs.feishu.cn，末尾不带斜杠
     */
    public FeishuAtsCollector(String source, String company, List<String> aliases, String baseUrl) {
        this.source = source;
        this.company = company;
        this.aliases = aliases == null ? List.of() : List.copyOf(aliases);
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    @Override
    public String source() {
        return source;
    }

    @Override
    public String company() {
        return company;
    }

    @Override
    public List<String> aliases() {
        return aliases;
    }

    @Override
    public List<RawJob> collect(HttpFetcher http) {
        JsonNode tokenResp = Json.parse(http.postJson(baseUrl + "/api/v1/csrf/token", "{\"portal_entrance\":1}",
                Map.of("Referer", baseUrl + "/campus/position")));
        String token = Json.text(tokenResp.path("data"), "token");
        if (token == null) {
            throw new CollectException(source + " 未拿到 csrf token: " + tokenResp);
        }
        Map<String, String> headers = Map.of(
                "x-csrf-token", token,
                "website-path", "campus",
                "portal-channel", "campus",
                "portal-platform", "pc",
                "Referer", baseUrl + "/campus/position");

        List<RawJob> out = new ArrayList<>();
        int offset = 0;
        for (int page = 0; page < MAX_PAGES; page++) {
            JsonNode root = Json.parse(http.postJson(baseUrl + "/api/v1/search/job/posts", Json.write(body(offset)), headers));
            Json.require(root.path("code").asInt(-1) == 0, source, root);
            JsonNode list = root.path("data").path("job_post_list");
            out.addAll(parse(root, baseUrl));
            offset += list.size();
            long total = Json.number(root.path("data"), "count");
            if (list.isEmpty() || offset >= total) {
                break;
            }
        }
        return out;
    }

    static Map<String, Object> body(int offset) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("keyword", "");
        body.put("limit", PAGE_SIZE);
        body.put("offset", offset);
        body.put("job_category_id_list", List.of());
        body.put("location_code_list", List.of());
        body.put("subject_id_list", List.of());
        body.put("recruitment_id_list", List.of());
        body.put("portal_type", 3);
        body.put("portal_entrance", 1);
        return body;
    }

    static List<RawJob> parse(JsonNode root, String baseUrl) {
        List<RawJob> out = new ArrayList<>();
        for (JsonNode p : root.path("data").path("job_post_list")) {
            JsonNode rt = p.path("recruit_type");
            // 校招页偶尔混进社招岗位，这里兜底过滤
            if ("社招".equals(Json.text(rt.path("parent"), "name"))) {
                continue;
            }
            String id = Json.text(p, "id");
            String title = Json.text(p, "title");
            out.add(new RawJob(id, title, Json.text(p.path("job_category"), "name"), recruitType(rt, title),
                    Texts.gradYear(title, Json.text(p, "requirement")),
                    Texts.cities(Json.texts(p.path("city_list"), "name")),
                    Json.text(p, "description"), Json.text(p, "requirement"),
                    baseUrl + "/campus/position/" + id + "/detail", Json.epochMillis(p, "publish_time")));
        }
        return out;
    }

    /**
     * 有的站点（如德赛西威）不返回 recruit_type，这时按标题判断
     */
    static RecruitType recruitType(JsonNode rt, String title) {
        String name = Json.text(rt, "name");
        if (name != null) {
            return "实习".equals(name) ? RecruitType.INTERN : RecruitType.CAMPUS;
        }
        return title != null && title.contains("实习") ? RecruitType.INTERN : RecruitType.CAMPUS;
    }
}
