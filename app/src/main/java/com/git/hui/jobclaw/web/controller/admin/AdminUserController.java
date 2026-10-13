package com.git.hui.jobclaw.web.controller.admin;

import com.git.hui.jobclaw.core.apis.permission.Permission;
import com.git.hui.jobclaw.core.apis.context.UserRoleEnum;
import com.git.hui.jobclaw.user.service.UserService;
import com.git.hui.jobclaw.core.utils.json.IntBaseEnum;
import com.git.hui.jobclaw.core.apis.PageListVo;
import com.git.hui.jobclaw.web.model.req.UserSearchReq;
import com.git.hui.jobclaw.web.model.res.UserVo;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台用户管理
 *
 * @date 2025/7/17
 */
@Permission(role = UserRoleEnum.ADMIN)
@RestController
@RequestMapping(path = "/api/admin/user")
public class AdminUserController {
    private final UserService userService;

    public AdminUserController(UserService userService) {
        this.userService = userService;
    }

    @RequestMapping(path = "list")
    public PageListVo<UserVo> list(UserSearchReq req) {
        return userService.searchUserList(req);
    }

    /**
     * 更新用户的权限
     *
     * @param userId     用户id
     * @param role       角色
     * @param expireTime VIP时，这里设置有效期
     * @return
     */
    @RequestMapping(path = "updateRole")
    public Boolean updateUserRole(Long userId, Integer role, Long expireTime) {
        Assert.notNull(userId, "用户id不能为空");
        Assert.notNull(role, "角色不能为空");
        UserRoleEnum roleEnum = IntBaseEnum.getEnumByCode(UserRoleEnum.class, role);
        Assert.notNull(roleEnum, "角色不能为空");
        return userService.updateUserRole(userId, roleEnum, expireTime);
    }
}
