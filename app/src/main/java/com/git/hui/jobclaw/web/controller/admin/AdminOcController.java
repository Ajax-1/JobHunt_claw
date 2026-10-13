package com.git.hui.jobclaw.web.controller.admin;

import com.git.hui.jobclaw.constants.oc.OcStateEnum;
import com.git.hui.jobclaw.core.apis.permission.Permission;
import com.git.hui.jobclaw.core.apis.context.UserRoleEnum;
import com.git.hui.jobclaw.oc.mcp.WechatBlogPublishService;
import com.git.hui.jobclaw.oc.service.OcService;
import com.git.hui.jobclaw.core.utils.json.IntBaseEnum;
import com.git.hui.jobclaw.core.apis.PageListVo;
import com.git.hui.jobclaw.web.model.req.OcSaveReq;
import com.git.hui.jobclaw.web.model.req.OcSearchReq;
import com.git.hui.jobclaw.web.model.res.OcVo;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.util.Asserts;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @date 2025/7/14
 */
@Slf4j
@Permission(role = UserRoleEnum.ADMIN)
@RestController
@RequestMapping(path = "/api/admin/oc")
@CrossOrigin
public class AdminOcController {
    private final OcService ocService;
    private final WechatBlogPublishService wechatBlogPublishService;


    public AdminOcController(OcService ocService, WechatBlogPublishService wechatBlogPublishService) {
        this.ocService = ocService;
        this.wechatBlogPublishService = wechatBlogPublishService;
    }

    @RequestMapping(path = "list")
    public PageListVo<OcVo> list(OcSearchReq req) {
        // 前台接口，只支持查询已发布的数据
        return ocService.searchOcList(req);
    }

    @PostMapping(path = "save")
    public boolean update(@RequestBody OcSaveReq req) {
        Asserts.notNull(req.getId(), "id can not be null");
        return ocService.updateOc(req);
    }

    @RequestMapping(path = "updateState")
    public boolean updateState(Long id, Integer state) {
        Asserts.notNull(id, "id can not be null");
        OcStateEnum stateEnum = IntBaseEnum.getEnumByCode(OcStateEnum.class, state);
        Asserts.notNull(stateEnum, "state can not be null");
        return ocService.updateState(id, stateEnum);
    }

    @GetMapping(path = "publish")
    public String publishBlog() {
        return wechatBlogPublishService.publishTodayOcInfo();
    }
}
