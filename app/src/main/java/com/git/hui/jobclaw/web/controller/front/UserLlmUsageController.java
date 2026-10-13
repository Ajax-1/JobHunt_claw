package com.git.hui.jobclaw.web.controller.front;

import com.git.hui.jobclaw.core.apis.PageListVo;
import com.git.hui.jobclaw.core.apis.context.ReqInfoContext;
import com.git.hui.jobclaw.core.apis.context.UserRoleEnum;
import com.git.hui.jobclaw.core.apis.permission.Permission;
import com.git.hui.jobclaw.llm.service.LlmUsageService;
import com.git.hui.jobclaw.llm.service.vo.LlmCallDetailVo;
import com.git.hui.jobclaw.llm.service.vo.LlmCallVo;
import com.git.hui.jobclaw.llm.service.vo.LlmOverviewVo;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Permission(role = UserRoleEnum.NORMAL)
@RestController
@RequestMapping("/api/user/llm-usage")
public class UserLlmUsageController {
    private final LlmUsageService service;

    public UserLlmUsageController(LlmUsageService service) {
        this.service = service;
    }

    private String uid() {
        return String.valueOf(ReqInfoContext.getReqInfo().getUserId());
    }

    @GetMapping("/overview")
    public LlmOverviewVo overview() {
        return service.overview(uid());
    }

    @GetMapping("/calls")
    public PageListVo<LlmCallVo> calls(String agent, String operation, String outcome, @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
        return service.calls(uid(), agent, operation, outcome, page, size);
    }

    @GetMapping("/calls/{id}")
    public LlmCallDetailVo detail(@PathVariable String id) {
        return service.userDetail(id, uid());
    }
}
