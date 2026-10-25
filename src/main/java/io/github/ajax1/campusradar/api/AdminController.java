package io.github.ajax1.campusradar.api;

import io.github.ajax1.campusradar.collect.CollectRun;
import io.github.ajax1.campusradar.collect.CollectService;
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
 * 手动触发采集，只允许直连的本机请求（{@link AccessFilter} 已拦截一次，这里再兜底校验）
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final CollectService collectService;

    public AdminController(CollectService collectService) {
        this.collectService = collectService;
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

    private static void requireLocal(HttpServletRequest request) {
        if (!AccessFilter.isDirectLocal(request)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只允许直连的本机请求");
        }
    }
}
