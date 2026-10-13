package com.git.hui.jobclaw.web.controller.admin;

import com.git.hui.jobclaw.core.apis.PageListVo;
import com.git.hui.jobclaw.core.apis.context.UserRoleEnum;
import com.git.hui.jobclaw.core.apis.permission.Permission;
import com.git.hui.jobclaw.llm.service.LlmUsageService;
import com.git.hui.jobclaw.llm.service.vo.LlmCallDetailVo;
import com.git.hui.jobclaw.llm.service.vo.LlmCallVo;
import com.git.hui.jobclaw.llm.service.vo.LlmOverviewVo;
import org.springframework.web.bind.annotation.*;

@Permission(role = UserRoleEnum.ADMIN)
@RestController
@RequestMapping("/api/admin/llm-monitor")
public class AdminLlmMonitorController {
    private final LlmUsageService service;

    public AdminLlmMonitorController(LlmUsageService service) {
        this.service = service;
    }

    @GetMapping("/overview")
    public LlmOverviewVo overview() {
        return service.overview(null);
    }

    @GetMapping("/calls")
    public PageListVo<LlmCallVo> calls(String userId, String agent, String operation, String outcome, @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
        return service.calls(userId, agent, operation, outcome, page, size);
    }

    @GetMapping("/calls/{id}")
    public LlmCallDetailVo detail(@PathVariable String id) {
        return service.adminDetail(id);
    }
}
