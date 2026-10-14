package com.git.hui.jobclaw.web.controller.front;

import com.git.hui.jobclaw.application.service.JobApplicationService;
import com.git.hui.jobclaw.core.apis.PageListVo;
import com.git.hui.jobclaw.core.apis.context.ReqInfoContext;
import com.git.hui.jobclaw.core.apis.context.UserRoleEnum;
import com.git.hui.jobclaw.core.apis.permission.Permission;
import com.git.hui.jobclaw.core.bizexception.BizException;
import com.git.hui.jobclaw.core.bizexception.StatusEnum;
import com.git.hui.jobclaw.web.model.req.ApplicationSaveReq;
import com.git.hui.jobclaw.web.model.req.ApplicationUpdateReq;
import com.git.hui.jobclaw.web.model.res.JobApplicationVo;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** AI-GENERATED AIDEV-NOTE: 所有者仅取登录上下文，不接受前端指定用户 ID。 */
@Permission(role = UserRoleEnum.NORMAL)
@RestController
@RequestMapping("/api/user/applications")
public class UserApplicationController {
    private final JobApplicationService applications;

    public UserApplicationController(JobApplicationService applications) {
        this.applications = applications;
    }

    @GetMapping
    public PageListVo<JobApplicationVo> list(@RequestParam(required = false) String status,
                                           @RequestParam(defaultValue = "1") int page,
                                           @RequestParam(defaultValue = "20") int size) {
        return applications.list(currentUserId(), status, page, size);
    }

    @PostMapping
    public JobApplicationVo save(@RequestBody ApplicationSaveReq request) {
        return applications.save(currentUserId(), request == null ? null : request.jobId());
    }

    @PatchMapping("/{id}")
    public JobApplicationVo update(@PathVariable Long id, @RequestBody ApplicationUpdateReq request) {
        return applications.update(currentUserId(), id, request);
    }

    @DeleteMapping("/{id}")
    public boolean delete(@PathVariable Long id) {
        return applications.delete(currentUserId(), id);
    }

    private Long currentUserId() {
        var context = ReqInfoContext.getReqInfo();
        if (context == null || context.getUserId() == null) {
            throw new BizException(StatusEnum.FORBID_NOTLOGIN);
        }
        return context.getUserId();
    }
}
