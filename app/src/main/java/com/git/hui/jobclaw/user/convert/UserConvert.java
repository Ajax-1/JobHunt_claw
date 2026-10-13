package com.git.hui.jobclaw.user.convert;

import com.git.hui.jobclaw.core.apis.context.UserBo;
import com.git.hui.jobclaw.core.apis.context.UserRoleEnum;
import com.git.hui.jobclaw.user.dao.entity.UserEntity;
import com.git.hui.jobclaw.user.dao.entity.UserInterestEntity;
import com.git.hui.jobclaw.core.utils.json.IntBaseEnum;
import com.git.hui.jobclaw.web.model.res.UserInterestVo;
import com.git.hui.jobclaw.web.model.res.UserVo;

import java.util.Collections;
import java.util.List;

/**
 * @date 2025/7/17
 */
public class UserConvert {

    public static UserBo toBo(UserEntity user) {
        return new UserBo(user.getId(), user.getDisplayName(), user.getAvatar(), IntBaseEnum.getEnumByCode(UserRoleEnum.class, user.getRole()));
    }

    public static UserVo toVo(UserEntity user) {
        long now = System.currentTimeMillis();
        if (user.getRole().equals(UserRoleEnum.VIP.getValue())) {
            if (user.getExpireTime() == null || user.getExpireTime().getTime() < now) {
                // vip失效
                user.setRole(UserRoleEnum.NORMAL.getValue());
            }
        }
        return new UserVo().setUserId(user.getId())
                .setRole(user.getRole())
                .setState(user.getState())
                .setAvatar(user.getAvatar())
                .setDisplayName(user.getDisplayName())
                .setExpireTime(user.getExpireTime() == null ? null : user.getExpireTime().getTime())
                .setCreateTime(user.getCreateTime().getTime())
                .setUpdateTime(user.getUpdateTime().getTime())
                .setDingDingId(user.getDingDingUserId())
                .setFeiShuId(user.getFeiShuUserId())
                .setEmail(user.getEmail())
                .setIntro(user.getIntro())
                .setWxId(user.getWxId());
    }

    public static List<UserVo> toVo(List<UserEntity> list) {
        if (list == null || list.isEmpty()) {
            return Collections.emptyList();
        }
        return list.stream().map(UserConvert::toVo).toList();
    }


    public static UserInterestVo toInterestVo(UserInterestEntity entity) {
        if (entity == null) return null;
        return new UserInterestVo(entity.getInterest()
                , entity.getCompanyType()
                , entity.getCompanyIndustry()
                , entity.getJobLocation()
                , entity.getRecruitmentType()
                , entity.getRecruitmentTarget()
                , entity.getPosition()
        );
    }
}
