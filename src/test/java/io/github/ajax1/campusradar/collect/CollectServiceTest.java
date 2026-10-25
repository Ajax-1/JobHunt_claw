package io.github.ajax1.campusradar.collect;

import io.github.ajax1.campusradar.RadarProperties;
import io.github.ajax1.campusradar.job.JobPosting;
import io.github.ajax1.campusradar.job.JobPostingRepository;
import io.github.ajax1.campusradar.job.JobQuery;
import io.github.ajax1.campusradar.job.JobQueryService;
import io.github.ajax1.campusradar.job.JobStatus;
import io.github.ajax1.campusradar.job.JobViews;
import io.github.ajax1.campusradar.job.RecruitType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 用假的采集器跑真实的入库、下线、健康检查逻辑
 */
@SpringBootTest
class CollectServiceTest {

    @Autowired
    JobUpsertService upsert;
    @Autowired
    CollectRunRepository runs;
    @Autowired
    JobPostingRepository jobs;
    @Autowired
    RadarProperties properties;
    @Autowired
    JobQueryService queryService;

    FakeCollector fake;
    CollectService service;

    @BeforeEach
    void setUp() {
        jobs.deleteAll();
        runs.deleteAll();
        fake = new FakeCollector();
        service = new CollectService(List.of(fake), upsert, runs, properties);
    }

    @Test
    void createsUpdatesAndClosesJobs() {
        fake.jobs = jobs(1, 10);
        CollectRun first = service.run("fake");
        assertThat(first.getStatus()).isEqualTo(CollectRun.Status.SUCCESS);
        assertThat(first.getCreated()).isEqualTo(10);

        // 第二轮：1 号岗位改了 JD，10 号岗位下线
        List<RawJob> second = new ArrayList<>(jobs(1, 9));
        second.set(0, job(1, "改过的职责"));
        fake.jobs = second;
        CollectRun run2 = service.run("fake");
        assertThat(run2.getStatus()).isEqualTo(CollectRun.Status.SUCCESS);
        assertThat(run2.getCreated()).isZero();
        assertThat(run2.getUpdated()).isEqualTo(1);
        assertThat(run2.getClosed()).isEqualTo(1);
        assertThat(jobs.findBySourceAndExternalId("fake", "10")).map(JobPosting::getStatus).contains(JobStatus.CLOSED);

        // 第三轮：10 号岗位又出现了，重新上线
        fake.jobs = jobs(1, 10);
        CollectRun run3 = service.run("fake");
        assertThat(jobs.findBySourceAndExternalId("fake", "10")).map(JobPosting::getStatus).contains(JobStatus.OPEN);
        assertThat(run3.getClosed()).isZero();
    }

    @Test
    void suspiciousDropIsWarnedAndDoesNotCloseJobs() {
        fake.jobs = jobs(1, 10);
        service.run("fake");

        fake.jobs = jobs(1, 3);
        CollectRun run = service.run("fake");
        assertThat(run.getStatus()).isEqualTo(CollectRun.Status.WARN);
        assertThat(run.getClosed()).isZero();
        assertThat(jobs.countBySourceAndStatus("fake", JobStatus.OPEN)).isEqualTo(10);
    }

    @Test
    void failureIsRecorded() {
        fake.fail = true;
        CollectRun run = service.run("fake");
        assertThat(run.getStatus()).isEqualTo(CollectRun.Status.FAILED);
        assertThat(run.getMessage()).contains("模拟失败");
    }

    @Test
    void detailFetchedOnlyForNewJobs() {
        fake.needsDetail = true;
        fake.jobs = jobs(1, 5).stream().map(j -> j.withDetail(null, null, null, null)).toList();
        service.run("fake");
        assertThat(fake.detailCalls).isEqualTo(5);
        assertThat(jobs.findBySourceAndExternalId("fake", "1")).map(JobPosting::getDescription).contains("详情 1");

        fake.jobs = jobs(1, 6).stream().map(j -> j.withDetail(null, null, null, null)).toList();
        CollectRun run = service.run("fake");
        assertThat(fake.detailCalls).isEqualTo(6);
        assertThat(run.getUpdated()).isZero();
        assertThat(run.getCreated()).isEqualTo(1);
    }

    @Test
    void staleDetailIsSkippedAndExistingJobClosed() {
        fake.needsDetail = true;
        fake.jobs = jobs(1, 5).stream().map(j -> j.withDetail(null, null, null, null)).toList();
        service.run("fake");

        // 3 号的 JD 被清空后详情又显示已下架；6 号是新岗位但详情已下架
        jobs.findBySourceAndExternalId("fake", "3").ifPresent(j -> {
            j.setDescription(null);
            jobs.save(j);
        });
        fake.staleIds = List.of("3", "6");
        fake.jobs = jobs(1, 6).stream().map(j -> j.withDetail(null, null, null, null)).toList();
        CollectRun run = service.run("fake");

        assertThat(run.getStatus()).isEqualTo(CollectRun.Status.SUCCESS);
        assertThat(run.getFetched()).isEqualTo(4);
        assertThat(run.getDetailFailed()).isZero();
        assertThat(jobs.findBySourceAndExternalId("fake", "6")).isEmpty();
        assertThat(jobs.findBySourceAndExternalId("fake", "3")).map(JobPosting::getStatus).contains(JobStatus.CLOSED);
    }

    @Test
    void searchFiltersByCityKeywordAndType() {
        fake.jobs = List.of(
                new RawJob("a", "后端开发工程师", "技术", RecruitType.CAMPUS, 2027, List.of("北京", "上海"),
                        "负责 Java 微服务开发", "熟悉 Spring", "https://example.com/a", null),
                new RawJob("b", "算法实习生", "技术", RecruitType.INTERN, null, List.of("深圳"),
                        "大模型训练", null, "https://example.com/b", null),
                new RawJob("c", "产品经理", "产品", RecruitType.CAMPUS, 2027, List.of("北京"),
                        "负责产品规划", null, "https://example.com/c", null));
        service.run("fake");

        assertThat(titles(new JobQuery(null, null, "北京市", null, null, null, 10)))
                .containsExactlyInAnyOrder("后端开发工程师", "产品经理");
        assertThat(titles(new JobQuery("java", null, null, null, null, null, 10)))
                .containsExactly("后端开发工程师");
        assertThat(titles(new JobQuery("大模型", null, null, RecruitType.INTERN, null, null, 10)))
                .containsExactly("算法实习生");
        assertThat(titles(new JobQuery(null, "测试公司", null, RecruitType.CAMPUS, 2027, 7, 10))).hasSize(2);
        assertThat(titles(new JobQuery(null, null, "北", null, null, null, 10))).isEmpty();
    }

    @Test
    void rankingInterleavingSnippetAndPostedAt() {
        // 大公司 6 个新岗位，小公司 1 个没有发布时间的岗位
        List<RawJob> big = new ArrayList<>();
        for (int i = 1; i <= 6; i++) {
            big.add(new RawJob("b" + i, "运营岗 " + i, "运营", RecruitType.CAMPUS, null, List.of("北京"),
                    "日常运营", null, "https://example.com/b" + i, java.time.Instant.now().minusSeconds(60L * i)));
        }
        big.add(new RawJob("b9", "数据分析", "数据", RecruitType.CAMPUS, null, List.of("北京"),
                "负责后端 Java 服务的数据埋点", null, "https://example.com/b9", java.time.Instant.now()));
        fake.jobs = big;
        service.run("fake");
        FakeCollector small = new FakeCollector() {
            @Override
            public String source() {
                return "small";
            }

            @Override
            public String company() {
                return "小公司";
            }
        };
        small.jobs = List.of(new RawJob("s1", "后端 Java 开发", "技术", RecruitType.CAMPUS, null, List.of("上海"),
                "微服务", null, "https://example.com/s1", null));
        new CollectService(List.of(small), upsert, runs, properties).run("small");

        // 没有发布时间的岗位按首次采集时间参与「最近 N 天」
        assertThat(titles(new JobQuery(null, null, null, null, null, 3, 50))).contains("后端 Java 开发");

        // 不限公司时轮流穿插：前两条来自两家公司
        List<JobViews.Summary> mixed = queryService.search(new JobQuery(null, null, null, null, null, null, 2));
        assertThat(mixed).extracting(JobViews.Summary::company).containsExactlyInAnyOrder("测试公司", "小公司");

        // 关键词按相关度：标题命中的排在只有 JD 命中的前面，JD 命中带片段
        List<JobViews.Summary> java = queryService.search(new JobQuery("java", "测试公司", null, null, null, null, 10));
        assertThat(java).extracting(JobViews.Summary::title).containsExactly("数据分析");
        assertThat(java.getFirst().snippet()).contains("后端 Java 服务");
        List<JobViews.Summary> all = queryService.search(new JobQuery("java", null, null, null, null, null, 10));
        assertThat(all.getFirst().title()).isEqualTo("后端 Java 开发");
        assertThat(all.getFirst().snippet()).isNull();
    }

    private List<String> titles(JobQuery q) {
        return queryService.search(q).stream().map(JobViews.Summary::title).toList();
    }

    private static List<RawJob> jobs(int from, int to) {
        return IntStream.rangeClosed(from, to).mapToObj(i -> job(i, "职责 " + i)).toList();
    }

    private static RawJob job(int i, String description) {
        return new RawJob(String.valueOf(i), "岗位 " + i, "技术", RecruitType.CAMPUS, 2027, List.of("北京"),
                description, "要求 " + i, "https://example.com/" + i, null);
    }

    static class FakeCollector implements JobCollector {
        List<RawJob> jobs = List.of();
        boolean fail;
        boolean needsDetail;
        int detailCalls;
        List<String> staleIds = List.of();

        @Override
        public String source() {
            return "fake";
        }

        @Override
        public String company() {
            return "测试公司";
        }

        @Override
        public List<RawJob> collect(HttpFetcher http) {
            if (fail) {
                throw new CollectException("模拟失败");
            }
            return jobs;
        }

        @Override
        public boolean needsDetail() {
            return needsDetail;
        }

        @Override
        public RawJob fetchDetail(RawJob summary, HttpFetcher http) {
            detailCalls++;
            if (staleIds.contains(summary.externalId())) {
                return null;
            }
            return summary.withDetail("技术", null, "详情 " + summary.externalId(), "要求");
        }
    }
}
