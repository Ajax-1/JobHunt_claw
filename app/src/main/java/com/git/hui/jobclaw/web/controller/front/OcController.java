package com.git.hui.jobclaw.web.controller.front;

import com.git.hui.jobclaw.core.apis.context.ReqInfoContext;
import com.git.hui.jobclaw.constants.oc.OcStateEnum;
import com.git.hui.jobclaw.core.apis.permission.Permission;
import com.git.hui.jobclaw.core.apis.context.UserRoleEnum;
import com.git.hui.jobclaw.oc.service.OcService;
import com.git.hui.jobclaw.core.apis.PageListVo;
import com.git.hui.jobclaw.web.model.req.OcSearchReq;
import com.git.hui.jobclaw.web.model.res.OcVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @date 2025/7/14
 */
@Slf4j
@Permission(role = UserRoleEnum.ALL)
@RestController
@RequestMapping(path = "/api/oc")
@CrossOrigin
public class OcController {
    private final OcService ocService;

    /**
     * 本地演示允许普通用户走通岗位闭环，生产环境默认保留会员限制。
     */
    @Value("${jobclaw.membership.require-vip:true}")
    private boolean membershipRequired;

    public OcController(OcService ocService) {
        this.ocService = ocService;
    }

    @RequestMapping(path = "list")
    public PageListVo<OcVo> list(OcSearchReq req) {
        // 前台接口，只支持查询已发布的数据
        req.setState(OcStateEnum.PUBLISHED.getValue());

        boolean locked = false;
        if (ReqInfoContext.getReqInfo().getUserId() == null) {
            // 未登录时，永远最多只返回5个
            req.setPage(1);
            req.setSize(5);
            locked = true;
        } else if (membershipRequired && ReqInfoContext.getReqInfo().getUser().role() == UserRoleEnum.NORMAL) {
            // 非会员，永远只能看第一页的数据
            req.setPage(1);
            req.setSize(9);
            locked = true;
        }
        PageListVo<OcVo> ans = ocService.searchOcList(req);
        ans.setLocked(locked);
        return ans;
    }


    @Permission(role = UserRoleEnum.NORMAL)
    @GetMapping(path = "detail")
    public OcVo detail(Long id) {
        return ocService.detail(id);
    }
}
