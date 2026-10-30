package io.github.ajax1.campusradar.push;

import io.github.ajax1.campusradar.RadarProperties;
import io.github.ajax1.campusradar.match.JobMatcher;
import io.github.ajax1.campusradar.match.MatchResult;
import io.github.ajax1.campusradar.profile.ProfileService;
import io.github.ajax1.campusradar.profile.UserProfile;
import io.github.ajax1.campusradar.schedule.EventView;
import io.github.ajax1.campusradar.schedule.ScheduleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * 每日推送：按档案匹配 → 跳过推过的 → 发企业微信 → 记录。发送失败不记录，下次还会推。
 */
@Service
public class DailyPushService {

    private static final Logger log = LoggerFactory.getLogger(DailyPushService.class);

    public enum Status {
        SENT,
        /**
         * 只生成了消息，没有发送
         */
        PREVIEW,
        /**
         * 没有新的匹配岗位，不打扰
         */
        NOTHING_NEW,
        /**
         * 没有配置 webhook
         */
        DISABLED
    }

    public record Outcome(Status status, List<MatchResult> matches, String markdown) {
    }

    private final ProfileService profiles;
    private final JobMatcher matcher;
    private final PushRecordRepository records;
    private final PushChannel channel;
    private final ScheduleService schedule;
    private final RadarProperties.Push config;

    public DailyPushService(ProfileService profiles, JobMatcher matcher, PushRecordRepository records,
                            PushChannel channel, ScheduleService schedule, RadarProperties properties) {
        this.profiles = profiles;
        this.matcher = matcher;
        this.records = records;
        this.channel = channel;
        this.schedule = schedule;
        this.config = properties.push();
    }

    /**
     * 生成今天会推送的内容，不发送也不记录
     */
    @Transactional(readOnly = true)
    public Outcome preview() {
        List<MatchResult> matches = pick();
        return new Outcome(matches.isEmpty() ? Status.NOTHING_NEW : Status.PREVIEW, matches,
                matches.isEmpty() ? null : withSchedule(format(matches, today())));
    }

    @Transactional
    public Outcome pushNow() {
        if (!channel.enabled()) {
            return new Outcome(Status.DISABLED, List.of(), null);
        }
        List<MatchResult> matches = pick();
        if (matches.isEmpty()) {
            log.info("没有新的匹配岗位，今天不推送");
            return new Outcome(Status.NOTHING_NEW, matches, null);
        }
        String markdown = withSchedule(format(matches, today()));
        channel.sendMarkdown(markdown);
        Instant now = Instant.now();
        for (MatchResult m : matches) {
            records.save(new PushRecord(UserProfile.DEFAULT_ID, m.job().id(), m.score(), now));
        }
        log.info("已推送 {} 个岗位", matches.size());
        return new Outcome(Status.SENT, matches, markdown);
    }

    private List<MatchResult> pick() {
        UserProfile profile = profiles.current();
        return matcher.match(profile, config.topN(), config.maxPerCompany(),
                records.findPushedJobIds(UserProfile.DEFAULT_ID));
    }

    /**
     * 推荐前面加上未来两天的笔试、面试；加上后超出 4096 字节就不加（日程另有单独提醒）
     */
    private String withSchedule(String jobs) {
        List<EventView> upcoming = schedule.upcoming(2);
        if (upcoming.isEmpty()) {
            return jobs;
        }
        StringBuilder sb = new StringBuilder("**近两天日程**\n");
        upcoming.forEach(e -> sb.append("> ").append(e.localTime()).append(" ").append(e.title()).append("\n"));
        String combined = sb.append("\n").append(jobs).toString();
        return combined.getBytes(StandardCharsets.UTF_8).length <= WeComPushChannel.MAX_BYTES ? combined : jobs;
    }

    private LocalDate today() {
        return LocalDate.now(ZoneId.of(config.zone()));
    }

    /**
     * 企业微信 markdown：支持链接、引用、font color。超过 4096 字节时先去掉理由，再从末尾减岗位。
     */
    static String format(List<MatchResult> matches, LocalDate day) {
        for (boolean withReasons : new boolean[]{true, false}) {
            List<MatchResult> items = new ArrayList<>(matches);
            while (!items.isEmpty()) {
                String text = render(items, day, withReasons);
                if (text.getBytes(StandardCharsets.UTF_8).length <= WeComPushChannel.MAX_BYTES) {
                    return text;
                }
                if (withReasons) {
                    break;
                }
                items.removeLast();
            }
        }
        throw new IllegalStateException("单条岗位就超过了企业微信消息上限");
    }

    private static String render(List<MatchResult> items, LocalDate day, boolean withReasons) {
        StringBuilder sb = new StringBuilder();
        sb.append("**校招雷达 · 今日推荐 ").append(items.size()).append(" 个岗位**（")
                .append(day.getMonthValue()).append("月").append(day.getDayOfMonth()).append("日）\n");
        int i = 1;
        for (MatchResult m : items) {
            var j = m.job();
            sb.append("\n").append(i++).append(". [").append(escape(j.company())).append(" · ")
                    .append(escape(j.title())).append("](").append(j.applyUrl()).append(")\n");
            sb.append("> <font color=\"comment\">")
                    .append(j.cities().isEmpty() ? "城市未注明" : String.join("/", j.cities().stream().limit(3).toList()))
                    .append(" · ").append(j.recruitType())
                    .append(" · 匹配 ").append(m.score()).append(" 分</font>");
            if (withReasons && !m.reasons().isEmpty()) {
                sb.append("\n> ").append(escape(String.join("；", m.reasons())));
            }
            sb.append("\n");
        }
        sb.append("\n<font color=\"comment\">投递请在官网完成；想调整推荐，改档案里的方向、技能和城市。</font>");
        return sb.toString();
    }

    /**
     * 标题里的方括号会打断 markdown 链接
     */
    private static String escape(String s) {
        return s == null ? "" : s.replace('[', '【').replace(']', '】');
    }
}
