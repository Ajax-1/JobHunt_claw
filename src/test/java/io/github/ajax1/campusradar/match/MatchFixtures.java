package io.github.ajax1.campusradar.match;

import io.github.ajax1.campusradar.collect.JobUpsertService;
import io.github.ajax1.campusradar.collect.RawJob;
import io.github.ajax1.campusradar.job.RecruitType;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 匹配和推送测试共用的岗位数据
 */
public final class MatchFixtures {

    private MatchFixtures() {
    }

    public static RawJob job(String id, String title, String category, RecruitType type, Integer gradYear,
                             List<String> cities, String description, int daysAgo) {
        return new RawJob(id, title, category, type, gradYear, cities, description, null,
                "https://example.com/" + id, Instant.now().minus(daysAgo, ChronoUnit.DAYS));
    }

    /**
     * 一组覆盖各种过滤条件的岗位：字节 4 个、阿里 2 个
     */
    public static void seed(JobUpsertService upsert) {
        Instant now = Instant.now();
        List<RawJob> bytedance = List.of(
                job("b1", "后端开发工程师-抖音", "研发", RecruitType.CAMPUS, 2027, List.of("北京"),
                        "使用 Java、Spring、MySQL 开发高并发服务，熟悉 Redis", 1),
                job("b2", "后端开发工程师-电商", "研发", RecruitType.CAMPUS, null, List.of("上海"),
                        "Go 或 Java 后端开发", 10),
                job("b3", "后端开发工程师-广告", "研发", RecruitType.CAMPUS, 2027, List.of("北京"),
                        "Java 微服务", 2),
                job("b4", "销售运营-后端支持", "运营", RecruitType.CAMPUS, 2027, List.of("北京"),
                        "支持后端业务的销售团队", 1));
        List<RawJob> alibaba = List.of(
                job("a1", "Java研发工程师", "技术类", RecruitType.CAMPUS, 2027, List.of("杭州"),
                        "Spring、MySQL、Redis 全链路开发", 5),
                job("a2", "算法实习生", "技术类", RecruitType.INTERN, null, List.of("北京"),
                        "大模型训练，熟悉 Python", 1),
                job("a3", "后端开发（2026届）", "技术类", RecruitType.CAMPUS, 2026, List.of("北京"),
                        "Java 后端", 1));
        bytedance.forEach(j -> upsert.upsert("bytedance", "字节跳动", j, now));
        alibaba.forEach(j -> upsert.upsert("alibaba", "阿里巴巴", j, now));
    }
}
