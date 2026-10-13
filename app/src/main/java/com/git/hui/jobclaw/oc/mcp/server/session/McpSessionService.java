package com.git.hui.jobclaw.oc.mcp.server.session;

import com.git.hui.jobclaw.core.apis.context.ReqInfoContext;
import com.git.hui.jobclaw.core.utils.json.JsonUtil;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpServerSession;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.lang.reflect.Field;
import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @date 2025/10/14
 */
@Slf4j
@Service
public class McpSessionService {
    @Autowired
    private McpClientRepository mcpClientRepository;

    /**
     * 保存会话上下文信息
     * - 在McpClient 发送 initialize 和 notifications/initialized 方法时触发
     *
     * @param sessionId  会话id
     * @param initInfo   initialize 方法的参数
     * @param notifyInfo notifications/initialized 方法的参数
     */
    public void saveSessionInfo(String sessionId, String initInfo, String notifyInfo) {
        McpClientEntity entity = mcpClientRepository.findBySessionId(sessionId);
        if (entity == null) {
            entity = new McpClientEntity();
            entity.setSessionId(sessionId);
            entity.setUserId(ReqInfoContext.getReqInfo().getUserId());
            entity.setCreateTime(new Date());
            entity.setNotifyInfo("");
            entity.setInitInfo("");
        }
        if (StringUtils.isNotBlank(initInfo)) {
            entity.setInitInfo(initInfo);
        }
        if (StringUtils.isNotBlank(notifyInfo)) {
            entity.setNotifyInfo(notifyInfo);
        }
        mcpClientRepository.save(entity);
        log.info("保存会话上下文信息: {}", entity);
    }


    /**
     * 若 McpServerSession 没有初始化，则尝试从数据库中获取上下文信息，进行手动初始化
     *
     * @param sessionId 会话id
     * @param session   会话对象
     * @return true 表示初始化成功 false 表示初始化失败
     */
    public boolean autoInitSession(String sessionId, McpServerSession session) {
        try {
            // 根据 io.modelcontextprotocol.spec.McpServerSession.state 状态来判断会话是否完成了初始化
            Field field = session.getClass().getDeclaredField("state");
            field.setAccessible(true);
            AtomicInteger value = (AtomicInteger) field.get(session);
            if (value.get() == 0) {
                // 表示没有初始化，需要手动初始化，优先根据会话ID进行查询 --> 这种适用于后端分布式部署的场景
                McpClientEntity entity = mcpClientRepository.findBySessionId(sessionId);
                if (entity == null) {
                    // 对于正好与客户端通过 /sse 端点建立连接的服务重启之后，因为请求断开；此时McpClient会重新请求/sse，此时会话id会发生变更
                    // 为了兼容上面这种场景，这里给出一个临时解决方案，根据用户来获取最近的一条会话信息，以此来进行初始化
                    entity = mcpClientRepository.findFirstByUserIdOrderByIdDesc(ReqInfoContext.getReqInfo().getUserId());
                }

                if (entity != null) {
                    // 初始化
                    Mono m = session.handle(JsonUtil.toObj(entity.getInitInfo(), McpSchema.JSONRPCRequest.class));
                    m.block();

                    // 通知
                    m = session.handle(JsonUtil.toObj(entity.getNotifyInfo(), McpSchema.JSONRPCNotification.class));
                    m.block();

                    log.info("自动加载上下文信息成功: {} - {}", sessionId, entity);
                    return true;
                } else {
                    log.info("自动加载上下文信息失败: {} - {}", sessionId, ReqInfoContext.getReqInfo().getUser());
                }
                return false;
            }
            return true;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
