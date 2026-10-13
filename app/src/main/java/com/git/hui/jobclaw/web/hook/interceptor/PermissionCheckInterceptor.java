package com.git.hui.jobclaw.web.hook.interceptor;


import com.git.hui.jobclaw.core.bizexception.StatusEnum;
import com.git.hui.jobclaw.core.apis.context.ReqInfoContext;
import com.git.hui.jobclaw.core.apis.context.UserBo;
import com.git.hui.jobclaw.core.utils.SpringUtil;
import com.git.hui.jobclaw.core.apis.permission.Permission;
import com.git.hui.jobclaw.core.apis.context.UserRoleEnum;
import com.git.hui.jobclaw.user.service.UserService;
import com.git.hui.jobclaw.core.utils.json.JsonUtil;
import com.git.hui.jobclaw.web.hook.filter.BodyReaderHttpServletRequestWrapper;
import com.git.hui.jobclaw.core.apis.ResVo;
import io.micrometer.common.util.StringUtils;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

/**
 * 权限管控拦截器
 *
 * @date 2025/7/15
 */
@Slf4j
@Component
public class PermissionCheckInterceptor implements HandlerInterceptor {
    @Value("${spring.ai.mcp.server.sse-endpoint:/sse}")
    private String sseUrl;
    @Value("${spring.ai.mcp.server.sse-message-endpoint:/mcp/messages}")
    private String msgUrl;

    @Value("${spring.ai.mcp.server.streamable-http.mcp-endpoint:/api/mcp}")
    private String mcpUrl;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (request.getDispatcherType() == DispatcherType.ASYNC) {
            return true;
        }

        if (handler instanceof HandlerMethod) {
            HandlerMethod handlerMethod = (HandlerMethod) handler;
            Permission permission = handlerMethod.getMethod().getAnnotation(Permission.class);
            if (permission == null) {
                permission = handlerMethod.getBeanType().getAnnotation(Permission.class);
            }

            if (permission == null || permission.role() == UserRoleEnum.ALL) {
                return true;
            }


            if (ReqInfoContext.getReqInfo() == null || ReqInfoContext.getReqInfo().getUserId() == null) {
                writeJson(response, ResVo.fail(StatusEnum.FORBID_NOTLOGIN));
                return false;
            }

            if (permission.role() == UserRoleEnum.ADMIN
                    && UserRoleEnum.ADMIN != ReqInfoContext.getReqInfo().getUser().role()) {
                // 设置为无权限
                response.setStatus(HttpStatus.FORBIDDEN.value());
                writeJson(response, ResVo.fail(StatusEnum.FORBID_ERROR_MIXED.getCode(), "无权限:请使用管理员账号登录"));
                return false;
            }

            if (permission.role() == UserRoleEnum.VIP
                    && UserRoleEnum.VIP != ReqInfoContext.getReqInfo().getUser().role()) {
                // 这里是会员专项的内容，无权访问
                writeJson(response, ResVo.fail(StatusEnum.FORBID_VIP_INFO));
                return false;
            }
        } else if (!checkMcpPermission(request)) {
            log.info("MCPServer 接口权限校验失败!");
            // mcp 相关校验 - 无权访问
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            writeJson(response, ResVo.fail(StatusEnum.FORBID_NOTLOGIN));
            return false;
        }
        return true;
    }

    private void writeJson(HttpServletResponse response, ResVo<?> body) throws IOException {
        response.setCharacterEncoding("UTF-8");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().println(JsonUtil.toStr(body));
        response.getWriter().flush();
    }

    private boolean checkMcpPermission(HttpServletRequest request) {
        if (!mcpUrl(request)) {
            // 不是mcp的请求，不做拦截
            return true;
        }

        if (request instanceof BodyReaderHttpServletRequestWrapper) {
            log.info("进入MCP权限校验: {} - {} - {}", request.getRequestURI(), request.getParameter("sessionId"), ((BodyReaderHttpServletRequestWrapper) request).getBodyString());
        } else {
            log.info("进入MCP权限校验: {} - {} - {}", request.getRequestURI(), request.getParameter("sessionId"), request.getQueryString());
        }
        // 表示是mcp的请求，需要进行权限管控
        String auth = request.getHeader("Authorization");
        if (StringUtils.isBlank(auth) || !auth.startsWith("Bearer ")) {
            return false;
        }
        // 根据token，获取用户信息，如果拿不到，则表明没有传用户身份，直接返回false，无权访问
        String token = auth.substring(7);
        UserBo user = SpringUtil.getBean(UserService.class).getUserByWxId(token);
        if (user == null) {
            return false;
        }

        // 设置用户上下文信息
        ReqInfoContext.getReqInfo().setUserId(user.userId());
        ReqInfoContext.getReqInfo().setUser(user);
        return true;
    }

    private boolean mcpUrl(HttpServletRequest request) {
        String reqUrl = request.getRequestURI();
        return reqUrl.equals(sseUrl) || reqUrl.equals(msgUrl) || reqUrl.equals(mcpUrl);
    }
}
