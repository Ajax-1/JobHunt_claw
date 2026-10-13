package com.git.hui.jobclaw.core.apis.service;

import com.git.hui.jobclaw.core.apis.context.UserBo;
import com.git.hui.jobclaw.core.apis.context.UserRoleEnum;
import com.git.hui.jobclaw.core.channel.ChannelConfig;

/**
 *
 * @date 2026/4/21
 */
public interface IUserService {
    UserBo getUser(String userId);

    UserBo getUser(String thirdId, ChannelConfig.ChannelEnum channel);

    default UserRoleEnum getRole(String userId) {
        if ("guest".equals(userId) || userId.startsWith("guest")) {
            return UserRoleEnum.NORMAL;
        }
        UserBo user = getUser(userId);
        return user == null ? UserRoleEnum.NORMAL : user.role();
    }
}
