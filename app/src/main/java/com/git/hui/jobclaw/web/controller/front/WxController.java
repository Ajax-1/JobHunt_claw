package com.git.hui.jobclaw.web.controller.front;

import cn.hutool.core.util.NumberUtil;
import com.git.hui.jobclaw.configs.service.CommonDictService;
import com.git.hui.jobclaw.constants.user.LoginConstants;
import com.git.hui.jobclaw.core.apis.context.UserBo;
import com.git.hui.jobclaw.core.apis.context.UserRoleEnum;
import com.git.hui.jobclaw.core.apis.permission.Permission;
import com.git.hui.jobclaw.core.utils.SpringUtil;
import com.git.hui.jobclaw.openapi.model.OpenApiUserDTO;
import com.git.hui.jobclaw.user.helper.SessionHelper;
import com.git.hui.jobclaw.user.helper.WxLoginProperties;
import com.git.hui.jobclaw.user.service.LoginService;
import com.git.hui.jobclaw.user.service.RechargeService;
import com.git.hui.jobclaw.user.service.UserService;
import com.git.hui.jobclaw.util.SessionUtil;
import com.git.hui.jobclaw.web.model.res.DevLoginVo;
import com.git.hui.jobclaw.web.model.wx.BaseWxMsgResVo;
import com.git.hui.jobclaw.web.model.wx.WxTxtMsgReqVo;
import com.git.hui.jobclaw.web.model.wx.WxTxtMsgResVo;
import io.micrometer.common.util.StringUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

/**
 * @date 2025/7/16
 */
@Slf4j
@Permission(role = UserRoleEnum.ALL)
@RestController
@RequestMapping(path = "/api/wx")
public class WxController {
    private final LoginService loginService;
    private final UserService userService;
    private final SessionHelper sessionHelper;
    private final CommonDictService commonDictService;

    private final RechargeService rechargeService;

    @Autowired
    public WxController(LoginService loginService, UserService userService, SessionHelper sessionHelper,
                        CommonDictService commonDictService, RechargeService rechargeService) {
        this.loginService = loginService;
        this.userService = userService;
        this.sessionHelper = sessionHelper;
        this.commonDictService = commonDictService;
        this.rechargeService = rechargeService;
    }

    /**
     * 本地开发直登，避免依赖微信公众号二维码。
     */
    @GetMapping(path = "/dev/login")
    public DevLoginVo devLogin(String type, HttpServletResponse response) {
        if (commonDictService.prodEnv()) {
            throw new IllegalStateException("生产环境不允许使用本地登录");
        }

        boolean admin = "admin".equalsIgnoreCase(type);
        String wxId = admin ? "demoUser-admin" : "demoUser-login";
        UserBo user = userService.getUserByWxId(wxId);
        if (user == null) {
            // AIDEV-NOTE: dev login only
            user = userService.autoRegisterExternalSsoUserInfo(buildDevUser(wxId, admin));
        }

        String session = sessionHelper.genSession(user);
        response.addCookie(SessionUtil.newCookie(LoginConstants.SESSION_KEY, session));
        return new DevLoginVo(session, userService.detail(user.userId()));
    }

    private OpenApiUserDTO buildDevUser(String wxId, boolean admin) {
        OpenApiUserDTO user = new OpenApiUserDTO();
        user.setWxId(wxId);
        user.setLoginName(wxId);
        user.setUserName(admin ? "管理员" : "普通用户");
        user.setRole(admin ? "admin" : "normal");
        user.setPhoto(admin
                ? "/favicon.svg"
                : "/favicon.svg");
        user.setEmail("");
        user.setProfile("");
        return user;
    }

    /**
     * 客户端与后端建立扫描二维码的长连接
     *
     * @return
     */
    @ResponseBody
    @GetMapping(value = "/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(String deviceId) throws IOException {
        HttpServletResponse response = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getResponse();
        if (response != null) {
            response.setHeader("Content-Type", "text/event-stream");
            response.setHeader("Cache-Control", "no-cache");
            response.setHeader("Connection", "keep-alive");
        }
        return loginService.subscribe();
    }

    /**
     * 刷新验证码
     *
     * @return
     * @throws IOException
     */
    @GetMapping(path = "/login/refresh")
    @ResponseBody
    public String refresh(String deviceId) throws IOException {
        return loginService.refreshCode();
    }


    /**
     * fixme: 做一个鉴权
     * 微信的响应返回
     * 本地测试访问: curl -X POST 'http://localhost:8080/api/wx/callback' -H 'content-type:application/xml' -d '<xml><URL><![CDATA[https://jobclaw.local]]></URL><ToUserName><![CDATA[JobClaw]]></ToUserName><FromUserName><![CDATA[demoUser1234]]></FromUserName><CreateTime>1655700579</CreateTime><MsgType><![CDATA[text]]></MsgType><Content><![CDATA[login]]></Content><MsgId>11111111</MsgId></xml>' -i
     *
     * @param msg
     * @return
     */
    @PostMapping(path = "callback",
            consumes = {"application/xml", "text/xml"},
            produces = "application/xml;charset=utf-8")
    public BaseWxMsgResVo callBack(@RequestBody WxTxtMsgReqVo msg) {
        String content = msg.getContent();
        if ("subscribe".equals(msg.getEvent()) || "scan".equalsIgnoreCase(msg.getEvent())) {
            String key = msg.getEventKey();
            if (StringUtils.isNotBlank(key)) {
                // 对于关注事件，key的格式为 qrscene_验证码； 对于扫码事件，key的格式就是 验证码
                String code;
                if (key.startsWith("qrscene_")) {
                    code = key.substring(8);
                } else {
                    code = key;
                }

                userService.autoRegisterWxUserInfo(msg.getFromUserName());
                String ans;
                if (NumberUtil.isNumber(code) && code.length() == 4) {
                    // 登录的场景
                    ans = loginService.login(code) ? "登录成功" : "登录失败，请输入验证码";
                } else {
                    ans = "subscribe".equals(msg.getEvent()) ? SpringUtil.getBean(WxLoginProperties.class).getSubscribeWelcomeInfo() : "";
                }
                WxTxtMsgResVo res = new WxTxtMsgResVo();
                res.setContent(ans);
                fillResVo(res, msg);
                return res;
            }
        }

        if (NumberUtil.isNumber(content)) {
            // 验证码登录方式，首先自动注册一个用户；然后再实现登录跳转
            userService.autoRegisterWxUserInfo(msg.getFromUserName());
            WxTxtMsgResVo res = new WxTxtMsgResVo();
            res.setContent(loginService.login(content) ? "登录成功" : "验证码过期了，刷新验证码再试试吧~");
            fillResVo(res, msg);
            return res;
        } else {
            WxTxtMsgResVo res = new WxTxtMsgResVo();
            res.setContent("这个关键词没有触发任何逻辑哦~");
            fillResVo(res, msg);
            return res;
        }
    }

    /**
     * 外部账号转发的微信公众号登录回调
     *
     * @param msg
     * @return
     */
    @PostMapping(path = "jobclaw/callback")
    public BaseWxMsgResVo autoLogin(@RequestBody WxTxtMsgReqVo msg) {
        return callBack(msg);
    }

    private void fillResVo(BaseWxMsgResVo res, WxTxtMsgReqVo msg) {
        res.setFromUserName(msg.getToUserName());
        res.setToUserName(msg.getFromUserName());
        res.setCreateTime(System.currentTimeMillis() / 1000);
    }


    /**
     * 微信支付回调
     *
     * @param request
     * @return
     */
    @PostMapping(path = "payNotify")
    public ResponseEntity<?> wxPayCallback(HttpServletRequest request) {
        return rechargeService.payCallback(request, rechargeService::payed);
    }
}
