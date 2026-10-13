package com.git.hui.jobclaw.web.controller.front;

import com.git.hui.jobclaw.configs.service.CommonDictService;
import com.git.hui.jobclaw.constants.dicts.DictAppEnum;
import com.git.hui.jobclaw.core.apis.context.UserRoleEnum;
import com.git.hui.jobclaw.core.apis.permission.Permission;
import com.git.hui.jobclaw.web.model.res.CommonDictVo;
import com.git.hui.jobclaw.web.model.res.DictItemVo;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * @date 2025/7/21
 */
@Permission(role = UserRoleEnum.ALL)
@RestController
@RequestMapping(path = "/api/common")
public class CommonController {
    private final CommonDictService commonDictService;

    public CommonController(CommonDictService commonDictService) {
        this.commonDictService = commonDictService;
    }

    @RequestMapping(path = "dict", produces = "application/json;charset=UTF-8")
    public List<CommonDictVo> dicts() {
        var list = commonDictService.queryPublicDictList();
        if (CollectionUtils.isEmpty(list)) {
            // 初始场景，没有字典；通常是首次启动，我们先给环境设置为dev，方便前台注入
            CommonDictVo vo = new CommonDictVo(DictAppEnum.SITE.getValue(), List.of(new DictItemVo("env", "dev", "环境")));
            return List.of(vo);
        }
        return list;
    }

}
