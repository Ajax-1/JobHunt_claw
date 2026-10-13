package com.git.hui.jobclaw.openapi;

import com.git.hui.jobclaw.core.apis.context.ReqInfoContext;
import com.git.hui.jobclaw.core.apis.context.UserBo;
import com.git.hui.jobclaw.constants.user.LoginConstants;
import com.git.hui.jobclaw.openapi.client.ExternalSsoLoginClient;
import com.git.hui.jobclaw.openapi.model.OpenApiUserDTO;
import com.git.hui.jobclaw.user.helper.SessionHelper;
import com.git.hui.jobclaw.user.service.UserService;
import com.git.hui.jobclaw.util.SessionUtil;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 通过外部账号登录服务
 *
 * @date 2025/9/15
 */
@Slf4j
@Component
public class ExternalSsoLoginHelper {
    public static final String EXTERNAL_SSO_TOKEN_NAME = "external-sso-session";

    @Autowired
    private ExternalSsoLoginClient externalSsoLoginClient;

    @Autowired
    private SessionHelper sessionHelper;

    @Autowired
    private UserService userService;


    /**
     * 从cookie或者请求头中，获取外部账号的登录信息
     *
     * @param request
     * @return
     */
    public UserBo loginByExternalSso(HttpServletRequest request, HttpServletResponse response) {
        if (request.getRequestURI().contains("logout")) {
            return null;
        }

        String externalSsoJwt = getExternalSsoToken(request);
        return StringUtils.isNotBlank(externalSsoJwt) ? loginByExternalSso(externalSsoJwt, response) : null;
    }


    /**
     * 判断外部账号的用户是否发生变化，若是，则同步更新账号
     *
     * @param request            请求
     * @param response           返回
     * @param lastExternalSsoUserId 上一次登录的外部账号用户id
     * @return
     */
    public ExternalSsoUserStatus externalSsoUserChanged(HttpServletRequest request, HttpServletResponse response, Long lastExternalSsoUserId) {
        String jwt = getExternalSsoToken(request);
        if (jwt == null) {
            // 外部账号已退出，JobClaw需要同步退出
            return ExternalSsoUserStatus.LOGOUT;
        }
        Map<String, Object> map = sessionHelper.getPayloadWithoutVerify(jwt);
        Object u = map.get("u");
        if (u == null) {
            // 外部账号登录信息有误
            return ExternalSsoUserStatus.SAME;
        }
        Long externalUserId = Long.valueOf(map.get("u").toString());
        if (lastExternalSsoUserId.equals(externalUserId)) {
            // 账号没有发生变化
            return ExternalSsoUserStatus.SAME;
        }

        // 外部账号的账号发生了切换，我们需要同步切换账号
        loginByExternalSso(request, response);
        log.info("外部账号登录账号发生变化，同步切换账号 {} -> {}", lastExternalSsoUserId, externalUserId);
        return ExternalSsoUserStatus.CHANGED;
    }

    public enum ExternalSsoUserStatus {
        // 同一个账号
        SAME,
        // 账号切换
        CHANGED,
        // 已登出
        LOGOUT,
        ;
    }


    private String getExternalSsoToken(HttpServletRequest request) {
        // 从cookie获取登录信息，适用于一级域名相同的场景
        Cookie ck = SessionUtil.findCookieByName(request, EXTERNAL_SSO_TOKEN_NAME);
        if (ck != null) {
            return ck.getValue();
        }


        // 从请求头中获取，适用于iframe嵌入请求方式、域名不同的场景
        String session = request.getHeader(EXTERNAL_SSO_TOKEN_NAME);
        if (StringUtils.isNotBlank(session)) {
            return session;
        }

        // 从请求参数中获取，适用于iframe嵌入请求方式、域名不同、外部授权的场景
        session = request.getParameter(EXTERNAL_SSO_TOKEN_NAME);
        return StringUtils.isNotBlank(session) ? session : null;
    }

    public UserBo loginByExternalSso(String session, HttpServletResponse response) {
        if (StringUtils.isBlank(session)) {
            return null;
        }
        OpenApiUserDTO openUser = externalSsoLoginClient.loginBySession(session);
        if (openUser == null) {
            return null;
        }

        UserBo user;
        // 如果外部账号的用户，有wxid，则基于wxid来登录
        if (StringUtils.isNotBlank(openUser.getWxId())) {
            user = userService.autoRegisterExternalSsoUserInfo(openUser);
        } else if (StringUtils.isNotBlank(openUser.getZsxqId()) && openUser.getLoginName().startsWith("zsxq_")) {
            // 基于知识星球用户的登录，同样使用用户名方式进行匹配
            // fixme 对于星球登录，需要判断，是否需要同步会员时间
            user = userService.autoRegisterExternalSsoUserInfo(openUser);
        } else {
            // 普通的用户名密码登录方式，统一给本地用户添加外部账号前缀
            openUser.setLoginName("external_" + openUser.getLoginName());
            user = userService.autoRegisterExternalSsoUserInfo(openUser);
        }

        // 自动生成本地 session，写入外部账号信息，用于支持账号切换
        String ocSession = sessionHelper.genSession(user, openUser.getUserId());
        response.addCookie(SessionUtil.newCookie(LoginConstants.SESSION_KEY, ocSession));
        ReqInfoContext.getReqInfo().setSession(ocSession);
        return user;
    }
}
