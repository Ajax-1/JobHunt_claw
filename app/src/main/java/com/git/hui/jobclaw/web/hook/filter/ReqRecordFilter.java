package com.git.hui.jobclaw.web.hook.filter;

import com.git.hui.jobclaw.core.apis.context.ReqInfoContext;
import com.git.hui.jobclaw.core.apis.context.UserBo;
import com.git.hui.jobclaw.core.utils.SpringUtil;
import com.git.hui.jobclaw.constants.user.LoginConstants;
import com.git.hui.jobclaw.openapi.ExternalSsoLoginHelper;
import com.git.hui.jobclaw.user.helper.SessionHelper;
import com.git.hui.jobclaw.user.service.UserService;
import com.git.hui.jobclaw.util.CrossUtil;
import com.git.hui.jobclaw.util.IpUtil;
import com.git.hui.jobclaw.util.SessionUtil;
import io.micrometer.common.util.StringUtils;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;

import java.io.IOException;
import java.net.URLDecoder;
import java.util.Map;
import java.util.UUID;

/**
 * 1. 请求参数日志输出过滤器
 * 2. 判断用户是否登录
 *
 * @date 2022/7/6
 */
@Slf4j
@WebFilter(urlPatterns = "/*", filterName = "reqRecordFilter", asyncSupported = true)
public class ReqRecordFilter implements Filter {
    private static Logger REQ_LOG = LoggerFactory.getLogger("req");

    @Override
    public void init(FilterConfig filterConfig) {
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        long start = System.currentTimeMillis();
        HttpServletRequest request = null;
        try {
            request = this.initReqInfo((HttpServletRequest) servletRequest, (HttpServletResponse) servletResponse);
            CrossUtil.buildCors(request, (HttpServletResponse) servletResponse);
            filterChain.doFilter(request, servletResponse);
        } finally {
            buildRequestLog(ReqInfoContext.getReqInfo(), request, System.currentTimeMillis() - start);
            ReqInfoContext.clear();
        }
    }

    @Override
    public void destroy() {
    }

    private HttpServletRequest initReqInfo(HttpServletRequest request, HttpServletResponse response) {
        if (isStaticURI(request)) {
            // 静态资源直接放行
            return request;
        }

        try {
            // 手动写入一个session，借助 OnlineUserCountListener 实现在线人数实时统计
            request.getSession().setAttribute("latestVisit", System.currentTimeMillis());

            ReqInfoContext.ReqInfo reqInfo = new ReqInfoContext.ReqInfo();
            reqInfo.setHost(request.getHeader("host"));
            reqInfo.setPath(request.getPathInfo());
            if (reqInfo.getPath() == null) {
                String url = request.getRequestURI();
                int index = url.indexOf("?");
                if (index > 0) {
                    url = url.substring(0, index);
                }
                reqInfo.setPath(url);
            }
            reqInfo.setReferer(request.getHeader("referer"));
            reqInfo.setClientIp(IpUtil.getClientIp(request));
            reqInfo.setUserAgent(request.getHeader("User-Agent"));
            reqInfo.setDeviceId(getOrInitDeviceId(request, response));

            request = this.wrapperRequest(request, reqInfo);


            // 注入用户信息
            initUserInfo(request, response, reqInfo);
            ReqInfoContext.addReqInfo(reqInfo);
        } catch (Exception e) {
            log.error("init reqInfo error!", e);
        }

        return request;
    }

    private void initUserInfo(HttpServletRequest request, HttpServletResponse response, ReqInfoContext.ReqInfo reqInfo) {
        String token = request.getHeader(LoginConstants.TOKEN_KEY);
        if (StringUtils.isBlank(token)) {
            Cookie ck = SessionUtil.findCookieByName(request, LoginConstants.SESSION_KEY);
            if (ck != null) {
                token = ck.getValue();
            }
        }

        if (StringUtils.isBlank(token)) {
            // JobClaw未登陆时，尝试使用 external SSO 的登录方式
            ReqInfoContext.addReqInfo(reqInfo);
            SpringUtil.getBean(ExternalSsoLoginHelper.class).loginByExternalSso(request, response);
            return;
        }

        // 如果是登陆状态，则尝试解析jwt中的令牌信息
        Map<String, Object> payload = SpringUtil.getBean(SessionHelper.class).getPayloadWithoutVerify(token);
        Object externalSsoUserId = payload.get("pid");
        if (externalSsoUserId != null) {
            // 表示当前用户是基于外部账号账号登陆的，我们需要验证下外部账号的账号是否有发生变更：这里主要是为了避免外部账号切换了账号，但是JobClaw还是老账号的情况
            ExternalSsoLoginHelper.ExternalSsoUserStatus status = SpringUtil.getBean(ExternalSsoLoginHelper.class).externalSsoUserChanged(request, response, Long.parseLong(externalSsoUserId.toString()));
            if (status == ExternalSsoLoginHelper.ExternalSsoUserStatus.CHANGED || status == ExternalSsoLoginHelper.ExternalSsoUserStatus.LOGOUT) {
                return;
            }
        }

        Long userId = SpringUtil.getBean(SessionHelper.class).getUserIdBySession(token);
        if (userId == null) {
            // 未登录
            return;
        }
        UserBo userBo = SpringUtil.getBean(UserService.class).getUserBo(userId);
        if (userBo == null) {
            return;
        }
        reqInfo.setUserId(userBo.userId());
        reqInfo.setUser(userBo);
        reqInfo.setSession(token);
    }

    private void buildRequestLog(ReqInfoContext.ReqInfo req, HttpServletRequest request, long costTime) {
        if (req == null || isStaticURI(request)) {
            return;
        }

        StringBuilder msg = new StringBuilder();
        msg.append("method=").append(request.getMethod()).append("; ");
        if (StringUtils.isNotBlank(req.getReferer())) {
            msg.append("referer=").append(URLDecoder.decode(req.getReferer())).append("; ");
        }
        msg.append("remoteIp=").append(req.getClientIp());
        msg.append("; agent=").append(req.getUserAgent());

        if (req.getUserId() != null) {
            // 打印用户信息
            msg.append("; user=").append(req.getUserId());
        }

        msg.append("; uri=").append(request.getRequestURI());
        if (StringUtils.isNotBlank(request.getQueryString())) {
            msg.append('?').append(URLDecoder.decode(request.getQueryString()));
        }

        msg.append("; payload=").append(req.getPayload());
        msg.append("; cost=").append(costTime);
        REQ_LOG.info("{}", msg);

    }


    private HttpServletRequest wrapperRequest(HttpServletRequest request, ReqInfoContext.ReqInfo reqInfo) {
        if (!HttpMethod.POST.name().equalsIgnoreCase(request.getMethod())) {
            return request;
        }

        BodyReaderHttpServletRequestWrapper requestWrapper = new BodyReaderHttpServletRequestWrapper(request);
        reqInfo.setPayload(requestWrapper.getBodyString());
        return requestWrapper;
    }

    private boolean isStaticURI(HttpServletRequest request) {
        return request == null
                || request.getRequestURI().endsWith("css")
                || request.getRequestURI().endsWith("js")
                || request.getRequestURI().endsWith("png")
                || request.getRequestURI().endsWith("ico")
                || request.getRequestURI().endsWith("gif")
                || request.getRequestURI().endsWith("svg")
                || request.getRequestURI().endsWith("min.js.map")
                || request.getRequestURI().endsWith("min.css.map");
    }

    /**
     * 初始化设备id
     *
     * @return
     */
    private String getOrInitDeviceId(HttpServletRequest request, HttpServletResponse response) {
        String deviceId = request.getParameter("deviceId");
        if (StringUtils.isNotBlank(deviceId) && !"null".equalsIgnoreCase(deviceId)) {
            return deviceId;
        }

        Cookie device = SessionUtil.findCookieByName(request, LoginConstants.USER_DEVICE_KEY);
        if (device == null) {
            deviceId = UUID.randomUUID().toString();
            if (response != null) {
                response.addCookie(SessionUtil.newCookie(LoginConstants.USER_DEVICE_KEY, deviceId));
            }
            return deviceId;
        }
        return device.getValue();
    }
}
