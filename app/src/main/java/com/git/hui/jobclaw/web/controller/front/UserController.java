package com.git.hui.jobclaw.web.controller.front;

import com.git.hui.jobclaw.constants.user.LoginConstants;
import com.git.hui.jobclaw.core.apis.PageListVo;
import com.git.hui.jobclaw.core.apis.PageReq;
import com.git.hui.jobclaw.core.apis.context.ReqInfoContext;
import com.git.hui.jobclaw.core.apis.context.UserRoleEnum;
import com.git.hui.jobclaw.core.apis.permission.Permission;
import com.git.hui.jobclaw.core.apis.req.UserInterestRecommendReq;
import com.git.hui.jobclaw.core.channel.ChannelConfig;
import com.git.hui.jobclaw.core.configuration.ConfigurationManager;
import com.git.hui.jobclaw.core.preference.AiUserPreferenceProperties;
import com.git.hui.jobclaw.core.preference.repository.AiUserPreferenceService;
import com.git.hui.jobclaw.core.utils.json.JsonUtil;
import com.git.hui.jobclaw.oc.service.OcService;
import com.git.hui.jobclaw.openapi.ExternalSsoLoginHelper;
import com.git.hui.jobclaw.user.service.UserInterestService;
import com.git.hui.jobclaw.user.service.UserService;
import com.git.hui.jobclaw.util.SessionUtil;
import com.git.hui.jobclaw.web.model.req.AiUserPreferenceReq;
import com.git.hui.jobclaw.web.model.req.UserSaveReq;
import com.git.hui.jobclaw.web.model.res.AiUserPreferenceVo;
import com.git.hui.jobclaw.web.model.res.OcVo;
import com.git.hui.jobclaw.web.model.res.UserVo;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static com.git.hui.jobclaw.core.preference.AiUserPreferenceProperties.CollectorType.AI_BASED;

/**
 * 后台用户管理
 *
 * @date 2025/7/17
 */
@Permission(role = UserRoleEnum.NORMAL)
@RestController
@RequestMapping(path = "/api/user")
public class UserController {
    private final UserService userService;

    private final UserInterestService userInterestService;

    private final OcService ocService;

    private final AiUserPreferenceProperties aiUserPreferenceProperties;

    private final AiUserPreferenceService aiUserPreferenceService;

    private final ConfigurationManager configurationManager;

    public UserController(UserService userService, UserInterestService userInterestService, OcService ocService,
                          AiUserPreferenceProperties aiUserPreferenceProperties,
                          AiUserPreferenceService aiUserPreferenceService,
                          ConfigurationManager configurationManager) {
        this.userService = userService;
        this.userInterestService = userInterestService;
        this.ocService = ocService;
        this.aiUserPreferenceProperties = aiUserPreferenceProperties;
        this.aiUserPreferenceService = aiUserPreferenceService;
        this.configurationManager = configurationManager;
    }


    /**
     * 更新用户信息
     *
     * @param user 用户id
     * @return
     */
    @RequestMapping(path = "update")
    public UserVo updateUserRole(@RequestBody UserSaveReq user) {
        user.setUserId(ReqInfoContext.getReqInfo().getUserId());
        return userService.updateUserInfo(user);
    }

    /**
     * 用户详情
     *
     * @return
     */
    @RequestMapping(path = "detail")
    public UserVo detail() {
        Long userId = ReqInfoContext.getReqInfo().getUserId();
        Assert.notNull(userId, "未登录");
        return userService.detail(userId);
    }

    /**
     * 提交自己的喜好
     *
     * @param text
     * @return
     */
    @RequestMapping(path = "interest")
    public UserVo submitInterest(String text) {
        Long userId = ReqInfoContext.getReqInfo().getUserId();
        Assert.notNull(userId, "未登录");
        userInterestService.submitInterest(text);
        return userService.detail(userId);
    }

    /**
     * 基于用户订阅偏好的岗位推荐
     *
     * @param req 分页参数
     * @return
     */
    @RequestMapping(path = "recommend")
    public PageListVo<OcVo> recommend(PageReq req) {
        UserInterestRecommendReq recommendReq = userInterestService.buildInterestRecommendReq(req);
        if (recommendReq == null) {
            // 用户还没有设置订阅偏好的场景下，不做任何推荐
            return PageListVo.emptyVo();
        }
        return ocService.recommendForUser(recommendReq);
    }


    /**
     * 账号登出
     *
     * @return
     */
    @RequestMapping(path = "logout")
    public boolean logout(HttpServletResponse response) {
        // 移除cookie
        response.addCookie(SessionUtil.delCookie(LoginConstants.SESSION_KEY));
        response.addCookie(SessionUtil.delCookie(ExternalSsoLoginHelper.EXTERNAL_SSO_TOKEN_NAME));
        return true;
    }

    /**
     * 获取用户偏好配置
     */
    @RequestMapping(path = "preference")
    public AiUserPreferenceVo getUserPreference() {
        Long userId = ReqInfoContext.getReqInfo().getUserId();
        Assert.notNull(userId, "未登录");

        // 优先查询db中的配置
        var preference = aiUserPreferenceService.findByUserId(userId);
        if (preference != null) {
            // 存在时，直接反序列化返回
            return JsonUtil.toObj(preference.getPreference(), AiUserPreferenceVo.class);
        }


        // 对于管理员的场景，可以从配置文件中解析
        AiUserPreferenceProperties.UserPreferenceEntry entry = aiUserPreferenceProperties.getUserPreference(String.valueOf(userId));
        if (entry == null) {
            return null;
        }
        return JsonUtil.toObj(JsonUtil.toStr(entry), AiUserPreferenceVo.class);
    }

    /**
     * 更新用户偏好配置
     */
    @RequestMapping(path = "preference/update")
    public boolean updateUserPreference(@RequestBody AiUserPreferenceReq req) {
        Long userId = ReqInfoContext.getReqInfo().getUserId();
        Assert.notNull(userId, "未登录");

        // 首先查出数据库中的配置
        var preferenceRecord = aiUserPreferenceService.findByUserId(userId);
        AiUserPreferenceVo vo;
        if (preferenceRecord == null) {
            vo = new AiUserPreferenceVo();
            vo.setCollector(AI_BASED.name());
            vo.setChannels(List.of(ChannelConfig.ChannelEnum.FEI_SHU.getChannel(), ChannelConfig.ChannelEnum.DING_DING.getChannel(),
                    ChannelConfig.ChannelEnum.WEXIN_CLAW_BOT.getChannel()));
            vo.setProviders(new HashMap<>());
        } else {
            vo = JsonUtil.toObj(preferenceRecord.getPreference(), AiUserPreferenceVo.class);
        }

        if (req.getCollector() != null) {
            vo.setCollector(req.getCollector());
        }
        if (!CollectionUtils.isEmpty(req.getChannels())) {
            vo.setChannels(req.getChannels());
        }
        if (req.getModels() != null) {
            vo.setModels(req.getModels());
        }
        if (req.isDeleteProvider()) {
            // 删除这个供应商的相关信息
            String providerName = req.getProvider().getProvider();
            vo.getProviders().remove(providerName);
        } else if (req.getProvider() != null) {
            // 修改or新增
            String providerName = req.getProvider().getProvider();
            var last = vo.getProviders().get(providerName);
            if (last == null) {
                vo.getProviders().put(providerName, req.getProvider());
                if (req.getModel() != null) {
                    req.getProvider().setModels(List.of(req.getModel()));
                } else {
                    req.getProvider().setModels(List.of());
                }
            } else {
                // 更新的场景
                var reqProvider = req.getProvider();
                if (reqProvider.getApiKey() != null && !reqProvider.getApiKey().contains("***")) {
                    last.setApiKey(reqProvider.getApiKey());
                }
                if (reqProvider.getApiStyle() != null) {
                    last.setApiStyle(reqProvider.getApiStyle());
                }
                if ("openai".equalsIgnoreCase(last.getApiStyle())) {
                    // 更新path路径
                    if (reqProvider.getBaseUrl() != null) {
                        last.setBaseUrl(reqProvider.getBaseUrl());
                    }
                    if (reqProvider.getCompletionsPath() != null) {
                        last.setCompletionsPath(reqProvider.getCompletionsPath());
                    }
                    if (reqProvider.getEmbeddingsPath() != null) {
                        last.setEmbeddingsPath(reqProvider.getEmbeddingsPath());
                    }
                    if (reqProvider.getImagesPath() != null) {
                        last.setImagesPath(reqProvider.getImagesPath());
                    }
                    if (reqProvider.getSpeechPath() != null) {
                        last.setSpeechPath(reqProvider.getSpeechPath());
                    }
                    if (reqProvider.getTranscriptionPath() != null) {
                        last.setTranscriptionPath(reqProvider.getTranscriptionPath());
                    }
                } else {
                    last.setBaseUrl("");
                    last.setCompletionsPath("");
                    last.setEmbeddingsPath("");
                    last.setImagesPath("");
                    last.setSpeechPath("");
                    last.setTranscriptionPath("");
                }


                if (req.getModel() != null) {
                    // 用户传入了model，有可能是新增、更新、删除
                    if (req.isDeleteModel()) {
                        // 找到之前的，删除掉
                        last.getModels().removeIf(model -> model.getName().equals(req.getModel().getName()));
                    } else {
                        boolean updated = false;
                        for (var model : last.getModels()) {
                            if (model.getName().equals(req.getModel().getName())) {
                                // 更新的场景
                                model.setType(req.getModel().getType());
                                model.setMultimodal(req.getModel().isMultimodal());
                                updated = true;
                                break;
                            }
                        }
                        if (!updated) {
                            // 新增
                            if (last.getModels() == null) {
                                last.setModels(new ArrayList<>());
                            }
                            last.getModels().add(req.getModel());
                        }
                    }
                } else if (last.getModels() == null) {
                    last.setModels(List.of());
                }
            }
        }


        aiUserPreferenceService.saveOrUpdate(userId, JsonUtil.toStr(vo));
        return true;
    }

}
