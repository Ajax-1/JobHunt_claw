package io.github.ajax1.campusradar.match;

import io.github.ajax1.campusradar.collect.JobUpsertService;
import io.github.ajax1.campusradar.job.JobPostingRepository;
import io.github.ajax1.campusradar.job.RecruitType;
import io.github.ajax1.campusradar.profile.UserProfile;
import io.github.ajax1.campusradar.push.PushRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class JobMatcherTest {

    @Autowired
    JobMatcher matcher;
    @Autowired
    JobUpsertService upsert;
    @Autowired
    JobPostingRepository jobs;
    @Autowired
    PushRecordRepository pushes;

    @BeforeEach
    void setUp() {
        pushes.deleteAll();
        jobs.deleteAll();
        MatchFixtures.seed(upsert);
    }

    private static UserProfile backendProfile() {
        UserProfile p = new UserProfile(UserProfile.DEFAULT_ID);
        p.setGradYear(2027);
        p.setTargetRoles(List.of("后端", "Java"));
        p.setSkills(List.of("Java", "Spring", "MySQL", "Redis"));
        p.setCities(List.of("北京", "杭州"));
        p.setRecruitTypes(List.of(RecruitType.CAMPUS));
        p.setExcludeKeywords(List.of("销售"));
        return p;
    }

    @Test
    void appliesHardFiltersAndRanksByScore() {
        List<MatchResult> results = matcher.match(backendProfile(), 10, 0, Set.of());
        List<String> titles = results.stream().map(r -> r.job().title()).toList();

        // 上海（不在意向城市）、实习、2026 届、标题含「销售」都被过滤
        assertThat(titles).containsExactly("后端开发工程师-抖音", "Java研发工程师", "后端开发工程师-广告");

        MatchResult top = results.getFirst();
        assertThat(top.score()).isGreaterThan(results.get(1).score());
        assertThat(top.reasons()).anyMatch(r -> r.startsWith("标题命中：后端"))
                .anyMatch(r -> r.equals("技能 4/4：Java、Spring、MySQL、Redis"))
                .anyMatch(r -> r.equals("城市：北京"))
                .anyMatch(r -> r.equals("1 天前发布"));
    }

    @Test
    void limitsJobsPerCompanyAndSkipsGivenIds() {
        List<MatchResult> capped = matcher.match(backendProfile(), 10, 1, Set.of());
        assertThat(capped).extracting(r -> r.job().company()).containsExactly("字节跳动", "阿里巴巴");

        long topId = capped.getFirst().job().id();
        List<MatchResult> skipped = matcher.match(backendProfile(), 10, 0, Set.of(topId));
        assertThat(skipped).extracting(r -> r.job().id()).doesNotContain(topId);
    }

    @Test
    void preferredCompanyAndExcludedCompany() {
        UserProfile p = backendProfile();
        p.setPreferredCompanies(List.of("阿里巴巴"));
        assertThat(matcher.match(p, 10, 0, Set.of()).getFirst().reasons()).contains("偏好公司");

        p.setExcludedCompanies(List.of("阿里巴巴"));
        assertThat(matcher.match(p, 10, 0, Set.of())).extracting(r -> r.job().company()).containsOnly("字节跳动");
    }

    @Test
    void scansBeyondOnePageAndIgnoresSubstringFalsePositives() {
        // 1200 个只提到 javascript 的岗位（java 不应命中），最后再插入一个真正匹配的岗位：它在最后一页
        java.time.Instant now = java.time.Instant.now();
        for (int i = 0; i < JobMatcher.PAGE_SIZE + 200; i++) {
            upsert.upsert("bulk", "字节跳动", MatchFixtures.job("bulk" + i, "前端开发工程师 " + i, "研发",
                    RecruitType.CAMPUS, 2027, List.of("北京"), "熟悉 javascript、google 生态", 1), now);
        }
        upsert.upsert("bulk", "字节跳动", MatchFixtures.job("last", "后端开发（最后一页）", "研发",
                RecruitType.CAMPUS, 2027, List.of("北京"), "Java、Spring、MySQL、Redis", 0), now);

        List<MatchResult> results = matcher.match(backendProfile(), 3, 0, Set.of());
        assertThat(results.getFirst().job().title()).isEqualTo("后端开发（最后一页）");
        assertThat(results).extracting(r -> r.job().title()).noneMatch(t -> t.startsWith("前端开发工程师"));
    }

    @Autowired
    io.github.ajax1.campusradar.application.ApplicationService applicationService;
    @Autowired
    io.github.ajax1.campusradar.application.ApplicationRepository applicationRepository;
    @Autowired
    io.github.ajax1.campusradar.application.ApplicationLogRepository applicationLogs;

    @Test
    void jobsAlreadyInLedgerAreNotRecommended() {
        applicationLogs.deleteAll();
        applicationRepository.deleteAll();
        long top = matcher.match(backendProfile(), 10, 0, Set.of()).getFirst().job().id();
        applicationService.record(null, null, top, null, null,
                io.github.ajax1.campusradar.application.Application.Source.MANUAL, null);
        assertThat(matcher.match(backendProfile(), 10, 0, Set.of())).extracting(r -> r.job().id()).doesNotContain(top);
        applicationLogs.deleteAll();
        applicationRepository.deleteAll();
    }

    @Test
    void emptyProfileIsRejected() {
        assertThatThrownBy(() -> matcher.match(new UserProfile(UserProfile.DEFAULT_ID), 5, 2, Set.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("update_profile");
    }
}
