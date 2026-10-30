package io.github.ajax1.campusradar.push;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PushScheduler {

    private static final Logger log = LoggerFactory.getLogger(PushScheduler.class);

    private final DailyPushService pushService;

    public PushScheduler(DailyPushService pushService) {
        this.pushService = pushService;
    }

    @Scheduled(cron = "${radar.push.cron}", zone = "${radar.push.zone}")
    public void daily() {
        try {
            DailyPushService.Outcome outcome = pushService.pushNow();
            log.info("每日推送结束: {}，{} 个岗位", outcome.status(), outcome.matches().size());
        } catch (RuntimeException e) {
            // 档案没填、webhook 失效等，不影响其他定时任务
            log.warn("每日推送失败: {}", e.getMessage());
        }
    }
}
