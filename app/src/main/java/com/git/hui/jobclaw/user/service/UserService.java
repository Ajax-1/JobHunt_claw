package com.git.hui.jobclaw.user.service;

import com.git.hui.jobclaw.components.id.IdUtil;
import com.git.hui.jobclaw.constants.common.BaseStateEnum;
import com.git.hui.jobclaw.constants.user.RechargeLevelEnum;
import com.git.hui.jobclaw.core.apis.PageListVo;
import com.git.hui.jobclaw.core.apis.context.ReqInfoContext;
import com.git.hui.jobclaw.core.apis.context.UserBo;
import com.git.hui.jobclaw.core.apis.context.UserRoleEnum;
import com.git.hui.jobclaw.core.apis.service.IUserService;
import com.git.hui.jobclaw.core.bizexception.BizException;
import com.git.hui.jobclaw.core.bizexception.StatusEnum;
import com.git.hui.jobclaw.core.channel.ChannelConfig;
import com.git.hui.jobclaw.core.utils.SpringUtil;
import com.git.hui.jobclaw.openapi.model.OpenApiUserDTO;
import com.git.hui.jobclaw.user.convert.UserConvert;
import com.git.hui.jobclaw.user.dao.entity.UserEntity;
import com.git.hui.jobclaw.user.dao.repository.UserRepository;
import com.git.hui.jobclaw.user.helper.UserRandomGenHelper;
import com.git.hui.jobclaw.util.DateUtil;
import com.git.hui.jobclaw.util.RandUtil;
import com.git.hui.jobclaw.web.config.SiteConfig;
import com.git.hui.jobclaw.web.model.req.UserSaveReq;
import com.git.hui.jobclaw.web.model.req.UserSearchReq;
import com.git.hui.jobclaw.web.model.res.McpConfigVo;
import com.git.hui.jobclaw.web.model.res.UserVo;
import io.micrometer.common.util.StringUtils;
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerProperties;
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerSseProperties;
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerStreamableHttpProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 用户服务
 *
 * @date 2025/7/16
 */
@Service
public class UserService implements IUserService {
    private final UserRepository userRepository;
    private final McpServerProperties mcpServerProperties;

    private final UserInterestService userInterestService;

    public UserService(UserRepository userRepository, McpServerProperties mcpServerProperties, UserInterestService userInterestService) {
        this.userRepository = userRepository;
        this.mcpServerProperties = mcpServerProperties;
        this.userInterestService = userInterestService;
    }

    public UserVo detail(Long userId) {
        Optional<UserEntity> user = userRepository.findById(userId);
        if (user.isEmpty() || user.get().getId() == null) {
            throw new BizException(StatusEnum.USER_NOT_EXISTS, userId);
        }

        UserVo vo = UserConvert.toVo(user.get());
        vo.setConfig(buildMcpConfig(user.get()));
        vo.setInterest(userInterestService.getUserInterest(userId));
        return vo;
    }

    private McpConfigVo buildMcpConfig(UserEntity user) {
        // AI-GENERATED AIDEV-NOTE: STATELESS 是服务端模式，客户端传输类型使用 http。
        String transport = mcpServerProperties.getProtocol() == McpServerProperties.ServerProtocol.SSE ? "sse" : "http";
        McpConfigVo configVo = new McpConfigVo(transport,
                SpringUtil.getBean(SiteConfig.class).getWebSiteUrl() + buildUrl(),
                mcpServerProperties.getVersion(),
                Map.of("Authorization", "Bearer " + user.getWxId())
        );
        return configVo;
    }

    private String buildUrl() {
        McpServerProperties.ServerProtocol protocol = mcpServerProperties.getProtocol();
        return switch (protocol) {
            case SSE -> SpringUtil.getBean(McpServerSseProperties.class).getSseEndpoint();
            default -> SpringUtil.getBean(McpServerStreamableHttpProperties.class).getMcpEndpoint();
        };
    }

    /**
     * 查询用户列表
     *
     * @param req
     * @return
     */
    public PageListVo<UserVo> searchUserList(UserSearchReq req) {
        if (req.getRole() != null && req.getRole().equals(UserRoleEnum.ALL.getValue())) {
            req.setRole(null);
        }
        PageListVo<UserEntity> res = userRepository.findList(req);
        List<UserVo> list = UserConvert.toVo(res.getList());
        return PageListVo.of(list, res.getTotal(), req.getPage(), req.getSize());
    }


    /**
     * 用户充值成功，自动更新对应的角色和过期时间
     *
     * @param userId   用户id
     * @param vipLevel 充值的会员级别
     * @return
     */
    public boolean updateUserVipInfo(Long userId, RechargeLevelEnum vipLevel) {
        UserEntity user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            throw new BizException(StatusEnum.RECORDS_NOT_EXISTS, "用户不存在");
        }

        if (user.getRole().equals(UserRoleEnum.ADMIN.getValue())) {
            // 管理员时，无需刷新过期时间
            return true;
        }

        long lastExpireTime = user.getExpireTime() == null ? System.currentTimeMillis() : user.getExpireTime().getTime();
        return updateUserRole(userId, UserRoleEnum.VIP, lastExpireTime + vipLevel.getMillSeconds());
    }

    /**
     * 更新用户角色和会员到期时间
     *
     * @param userId
     * @param role       用户角色
     * @param expireTime 到期时间
     * @return
     */
    public boolean updateUserRole(Long userId, UserRoleEnum role, Long expireTime) {
        UserEntity user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            throw new BizException(StatusEnum.RECORDS_NOT_EXISTS, "用户不存在");
        }
        if (Objects.equals(role, UserRoleEnum.VIP)) {
            // vip用户，要求过期时间存在
            if (expireTime == null) {
                expireTime = System.currentTimeMillis() + RechargeLevelEnum.MONTH.getMillSeconds();
            }
            user.setExpireTime(new Date(expireTime / DateUtil.ONE_DAY_MILL * DateUtil.ONE_DAY_MILL));
        }
        user.setRole(role.getValue());
        user.setUpdateTime(new Date());
        userRepository.saveAndFlush(user);
        return true;
    }

    /**
     * 用户修改自己的信息
     *
     * @param req
     * @return
     */
    public UserVo updateUserInfo(UserSaveReq req) {
        if (!Objects.equals(ReqInfoContext.getReqInfo().getUserId(), req.getUserId())) {
            throw new BizException(StatusEnum.FORBID_ERROR);
        }

        UserEntity user = userRepository.findById(req.getUserId()).orElse(null);
        if (user == null) {
            throw new BizException(StatusEnum.RECORDS_NOT_EXISTS, "用户不存在");
        }
        if (StringUtils.isNotBlank(req.getEmail())) {
            user.setEmail(req.getEmail());
        }
        if (StringUtils.isNotBlank(req.getIntro())) {
            user.setIntro(req.getIntro());
        }
        if (StringUtils.isNotBlank(req.getDisplayName())) {
            user.setDisplayName(req.getDisplayName());
        }
        if (StringUtils.isNotBlank(req.getAvatar())) {
            user.setAvatar(req.getAvatar());
        }
        if (StringUtils.isNotBlank(req.getDingDingId())) {
            user.setDingDingUserId(req.getDingDingId());
        }
        if (StringUtils.isNotBlank(req.getFeiShuId())) {
            user.setFeiShuUserId(req.getFeiShuId());
        }
        user.setUpdateTime(new Date());
        userRepository.saveAndFlush(user);
        return UserConvert.toVo(user);
    }


    @Transactional(rollbackFor = Exception.class)
    public UserBo autoRegisterWxUserInfo(String uuid) {
        UserSaveReq req = new UserSaveReq().setWxId(uuid);
        UserEntity user = registerOrGetUserInfo(req);
        UserBo bo = UserConvert.toBo(user);
        ReqInfoContext.getReqInfo().setUserId(user.getId());
        ReqInfoContext.getReqInfo().setUser(bo);
        return bo;
    }

    /**
     * 没有注册时，先注册一个用户；若已经有，则登录
     *
     * @param req
     */
    private UserEntity registerOrGetUserInfo(UserSaveReq req) {
        UserEntity user = userRepository.findByWxId(req.getWxId());
        if (user == null) {
            return registerByWx(req.getWxId());
        }
        return user;
    }


    /**
     * 微信用户注册
     *
     * @param wxId 微信三方 id
     * @return 用户id
     */
    private UserEntity registerByWx(String wxId) {
        UserEntity user = new UserEntity()
                .setId(IdUtil.genId())
                .setWxId(wxId)
                .setRole(UserRoleEnum.NORMAL.getValue())
                .setCreateTime(new Date())
                .setUpdateTime(new Date())
                .setState(BaseStateEnum.NORMAL_STATE.getValue())
                .setLoginName("")
                .setPassword("")
                .setEmail("")
                .setIntro("")
                .setDisplayName(UserRandomGenHelper.genNickName())
                .setAvatar(UserRandomGenHelper.genAvatar());
        userRepository.saveAndFlush(user);
        return user;
    }

    /**
     * 使用外部账号用户进行自动免登JobClaw
     *
     * @param openUser 外部账号用户
     * @return
     */
    @Transactional
    public UserBo autoRegisterExternalSsoUserInfo(OpenApiUserDTO openUser) {
        UserEntity user = StringUtils.isNotBlank(openUser.getWxId()) ?
                userRepository.findByWxId(openUser.getWxId()) :
                userRepository.findByLoginName(openUser.getLoginName());
        if (user == null) {
            // 自动注册
            user = registerByOpenUser(openUser);
        } else {
            // 自动同步用户昵称、头像等信息
            boolean toUpdate = false;
            if (!user.getDisplayName().equals(openUser.getUserName())) {
                toUpdate = true;
                user.setDisplayName(openUser.getUserName());
            }
            if (!user.getAvatar().equals(openUser.getPhoto())) {
                toUpdate = true;
                user.setAvatar(openUser.getPhoto());
            }
            if (!user.getEmail().equals(openUser.getEmail())) {
                toUpdate = true;
                user.setEmail(openUser.getEmail());
            }
            if (!user.getIntro().equals(openUser.getProfile())) {
                toUpdate = true;
                user.setIntro(openUser.getProfile());
            }
            if (!Objects.equals(user.getRole(), UserRoleEnum.ADMIN.getValue())) {
                if (StringUtils.isNotBlank(
                        openUser.getZsxqId()) && openUser.getZsxqExpireTime() != null && openUser.getZsxqExpireTime() > System.currentTimeMillis() / 1000) {
                    // 更新会员信息
                    if (user.getExpireTime().getTime() / 1000 != openUser.getZsxqExpireTime()) {
                        toUpdate = true;
                        user.setRole(UserRoleEnum.VIP.getValue());
                        user.setExpireTime(new Date(openUser.getZsxqExpireTime() * 1000));
                    }
                }
            }
            if (toUpdate) {
                userRepository.saveAndFlush(user);
            }
        }
        UserBo bo = UserConvert.toBo(user);
        ReqInfoContext.getReqInfo().setUserId(user.getId());
        ReqInfoContext.getReqInfo().setUser(bo);
        return bo;
    }

    private UserEntity registerByOpenUser(OpenApiUserDTO openUser) {
        UserRoleEnum role;
        if ("admin".equals(openUser.getRole())) {
            role = UserRoleEnum.ADMIN;
        } else if (openUser.getZsxqExpireTime() != null && openUser.getZsxqExpireTime() > System.currentTimeMillis() / 1000) {
            // 会员
            role = UserRoleEnum.VIP;
        } else {
            // 普通用户
            role = UserRoleEnum.NORMAL;
        }

        UserEntity user = new UserEntity()
                .setId(IdUtil.genId())
                .setWxId(StringUtils.isBlank(openUser.getWxId()) ? "" : openUser.getWxId())
                .setRole(role.getValue())
                .setExpireTime(openUser.getZsxqExpireTime() != null ? new Date(openUser.getZsxqExpireTime() * 1000) : null)
                .setCreateTime(new Date())
                .setUpdateTime(new Date())
                .setState(BaseStateEnum.NORMAL_STATE.getValue())
                .setLoginName(openUser.getLoginName())
                .setPassword(RandUtil.random(8))
                .setEmail(openUser.getEmail() == null ? "" : openUser.getEmail())
                .setIntro(openUser.getProfile() == null ? "" : openUser.getProfile())
                .setDisplayName(openUser.getUserName())
                .setAvatar(openUser.getPhoto());
        userRepository.saveAndFlush(user);
        return user;
    }


    @Override
    public UserBo getUser(String userId) {
        return getUserBo(Long.parseLong(userId));
    }

    @Override
    public UserBo getUser(String thirdId, ChannelConfig.ChannelEnum channel) {
        UserEntity user = switch (channel) {
            case DING_DING -> userRepository.findByDingDingUserId(thirdId);
            case FEI_SHU -> userRepository.findByFeiShuUserId(thirdId);
            default -> null;
        };
        if (user == null) {
            return null;
        }
        if (user.getRole().equals(UserRoleEnum.VIP.getValue())
                && System.currentTimeMillis() >= user.getExpireTime().getTime()) {
            // 会员已过期
            user.setRole(UserRoleEnum.NORMAL.getValue());
            userRepository.saveAndFlush(user);
        }
        return UserConvert.toBo(user);
    }

    /**
     * 获取用户信息
     *
     * @param userId
     * @return
     */
    public UserBo getUserBo(Long userId) {
        UserEntity user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return null;
        }
        if (user.getRole().equals(UserRoleEnum.VIP.getValue())
                && System.currentTimeMillis() >= user.getExpireTime().getTime()) {
            // 会员已过期
            user.setRole(UserRoleEnum.NORMAL.getValue());
            userRepository.saveAndFlush(user);
        }
        return UserConvert.toBo(user);
    }

    public UserBo getUserByWxId(String wxId) {
        UserEntity user = userRepository.findByWxId(wxId);
        if (user == null) {
            return null;
        }
        return UserConvert.toBo(user);
    }

    public List<UserBo> getUserByUserIds(List<Long> ids) {
        return userRepository.findByIdIn(ids).stream().map(UserConvert::toBo).toList();
    }
}
