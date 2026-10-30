package io.github.ajax1.campusradar.api;

import io.github.ajax1.campusradar.collect.CollectRun;
import io.github.ajax1.campusradar.collect.CollectService;
import io.github.ajax1.campusradar.mail.MailSyncService;
import io.github.ajax1.campusradar.push.DailyPushService;
import io.github.ajax1.campusradar.schedule.ReminderService;
import io.github.ajax1.campusradar.security.AccessFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * 手动触发采集和推送，只允许直连的本机请求（{@link AccessFilter} 已拦截一次，这里再兜底校验）
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final CollectService collectService;
    private final DailyPushService pushService;
    private final MailSyncService mailSync;
    private final ReminderService reminders;

    public AdminController(CollectService collectService, DailyPushService pushService, MailSyncService mailSync,
                           ReminderService reminders) {
        this.collectService = collectService;
        this.pushService = pushService;
        this.mailSync = mailSync;
        this.reminders = reminders;
    }

    /**
     * 同步采集单个数据源，返回本次运行结果
     */
    @PostMapping("/collect/{source}")
    public CollectRun collect(@PathVariable String source, HttpServletRequest request) {
        requireLocal(request);
        try {
            return collectService.run(source);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
    }

    /**
     * 后台采集全部数据源，立即返回
     */
    @PostMapping("/collect")
    public Map<String, Object> collectAll(HttpServletRequest request) {
        requireLocal(request);
        Thread.ofVirtual().name("collect-manual").start(collectService::runAll);
        return Map.of("started", true, "sources", collectService.sources());
    }

    /**
     * 预览今天会推送的内容，不发送、不记录
     */
    @PostMapping("/push/preview")
    public DailyPushService.Outcome previewPush(HttpServletRequest request) {
        requireLocal(request);
        return pushService.preview();
    }

    /**
     * 立即推送一次（和定时推送一样会跳过推过的岗位并记录）
     */
    @PostMapping("/push")
    public DailyPushService.Outcome pushNow(HttpServletRequest request) {
        requireLocal(request);
        return pushService.pushNow();
    }

    @PostMapping("/mail/sync")
    public MailSyncService.Report syncMail(HttpServletRequest request) {
        requireLocal(request);
        try {
            return mailSync.syncNow();
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    /**
     * 立即检查一次日程提醒（平时每 5 分钟自动检查）
     */
    @PostMapping("/remind")
    public Map<String, Object> remind(HttpServletRequest request) {
        requireLocal(request);
        return Map.of("reminded", reminders.remind(java.time.Instant.now()));
    }

    private static void requireLocal(HttpServletRequest request) {
        if (!AccessFilter.isDirectLocal(request)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只允许直连的本机请求");
        }
    }
}
