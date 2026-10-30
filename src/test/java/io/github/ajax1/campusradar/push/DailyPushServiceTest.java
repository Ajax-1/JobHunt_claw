package io.github.ajax1.campusradar.push;

import io.github.ajax1.campusradar.collect.JobUpsertService;
import io.github.ajax1.campusradar.job.JobPostingRepository;
import io.github.ajax1.campusradar.job.JobViews;
import io.github.ajax1.campusradar.match.MatchFixtures;
import io.github.ajax1.campusradar.match.MatchResult;
import io.github.ajax1.campusradar.profile.ProfileService;
import io.github.ajax1.campusradar.profile.ProfileView;
import io.github.ajax1.campusradar.profile.UserProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class DailyPushServiceTest {

    /**
     * 代替企业微信，把发出的消息记下来
     */
    static class CapturingChannel implements PushChannel {
        final List<String> sent = new ArrayList<>();
        boolean enabled = true;

        @Override
        public boolean enabled() {
            return enabled;
        }

        @Override
        public void sendMarkdown(String markdown) {
            sent.add(markdown);
        }
    }

    @TestConfiguration
    static class Config {
        @Bean
        @Primary
        CapturingChannel capturingChannel() {
            return new CapturingChannel();
        }
    }

    @Autowired
    DailyPushService pushService;
    @Autowired
    CapturingChannel channel;
    @Autowired
    ProfileService profiles;
    @Autowired
    UserProfileRepository profileRepository;
    @Autowired
    PushRecordRepository records;
    @Autowired
    JobPostingRepository jobs;
    @Autowired
    JobUpsertService upsert;

    @BeforeEach
    void setUp() {
        records.deleteAll();
        jobs.deleteAll();
        profileRepository.deleteAll();
        channel.sent.clear();
        channel.enabled = true;
        MatchFixtures.seed(upsert);
        profiles.update(new ProfileView(2027, null, null, List.of("后端", "Java"), List.of("Java", "Redis"),
                List.of("北京", "杭州"), List.of("校招"), null, null, List.of("销售"), null, null));
    }

    @Test
    void pushesOnceAndNeverRepeats() {
        DailyPushService.Outcome first = pushService.pushNow();
        assertThat(first.status()).isEqualTo(DailyPushService.Status.SENT);
        assertThat(first.matches()).hasSize(3);
        assertThat(channel.sent).hasSize(1);
        assertThat(channel.sent.getFirst())
                .contains("今日推荐 3 个岗位")
                .contains("[字节跳动 · 后端开发工程师-抖音](https://example.com/b1)")
                .contains("匹配 ");

        DailyPushService.Outcome second = pushService.pushNow();
        assertThat(second.status()).isEqualTo(DailyPushService.Status.NOTHING_NEW);
        assertThat(channel.sent).hasSize(1);
    }

    @Test
    void previewDoesNotSendOrRecord() {
        DailyPushService.Outcome preview = pushService.preview();
        assertThat(preview.status()).isEqualTo(DailyPushService.Status.PREVIEW);
        assertThat(preview.markdown()).contains("今日推荐");
        assertThat(channel.sent).isEmpty();
        assertThat(records.count()).isZero();
    }

    @Test
    void disabledChannelDoesNothing() {
        channel.enabled = false;
        assertThat(pushService.pushNow().status()).isEqualTo(DailyPushService.Status.DISABLED);
        assertThat(records.count()).isZero();
    }

    @Test
    void formatStaysUnderWeComLimitAndEscapesBrackets() {
        String longReason = "技能 9/9：" + "很长的技能名".repeat(60);
        List<MatchResult> many = IntStream.range(0, 5).mapToObj(i -> new MatchResult(
                new JobViews.Summary(i, "字节跳动", "【27届】[急招]后端开发工程师 " + i, "研发", "校招", 2027,
                        List.of("北京", "上海", "深圳", "杭州"), Instant.now(), Instant.now(), null, "OPEN",
                        "https://jobs.bytedance.com/campus/position/" + i + "/detail", null),
                90 - i, List.of(longReason, "城市：北京"))).toList();

        String text = DailyPushService.format(many, LocalDate.of(2026, 10, 25));
        assertThat(text.getBytes(StandardCharsets.UTF_8).length).isLessThanOrEqualTo(WeComPushChannel.MAX_BYTES);
        assertThat(text).contains("10月25日").contains("【急招】").doesNotContain("[急招]");
        assertThat(text).contains("北京/上海/深圳").doesNotContain("杭州");
    }
}
