package com.git.hui.jobclaw.channels.websocket;

import com.auth0.jwt.JWT;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;

import java.security.Principal;

/**
 * STOMP CONNECT 阶段的 JWT 认证拦截器
 * <p>
 * 从 WebSocket 握手头中提取 X-OC-TOKEN，验证 JWT 有效性，
 * 并将用户信息设置到 STOMP Session 的 Principal 中。
 */
@Slf4j
public class WsAuthChannelInterceptor implements ChannelInterceptor {

    private final String jwtSecret;
    private final String jwtIssuer;

    public WsAuthChannelInterceptor(String jwtSecret, String jwtIssuer) {
        this.jwtSecret = jwtSecret;
        this.jwtIssuer = jwtIssuer;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            String token = accessor.getFirstNativeHeader("X-OC-TOKEN");
            if (StringUtils.isBlank(token)) {
                // 尝试从握手头获取
                var sessionAttributes = accessor.getSessionAttributes();
                if (sessionAttributes != null) {
                    token = (String) sessionAttributes.get("X-OC-TOKEN");
                }
            }

            if (StringUtils.isBlank(token)) {
                log.warn("[WS-Auth] Missing token in CONNECT frame");
                throw new IllegalArgumentException("未提供认证令牌");
            }

            try {
                DecodedJWT jwt = JWT.require(com.auth0.jwt.algorithms.Algorithm.HMAC256(jwtSecret))
                        .withIssuer(jwtIssuer)
                        .build()
                        .verify(token);

                // 为了避免userId在jwt存的是数字类型，直接使用asString返回null，我们采用 as(String.class) 方式进行提取
                String userId = jwt.getClaim("uid").as(String.class);
                String nickname = jwt.getClaim("un").asString();

                if (StringUtils.isBlank(userId)) {
                    throw new JWTVerificationException("Invalid token: missing uid");
                }

                // 创建简单的 Principal 实现
                accessor.setUser(new WsPrincipal(userId, nickname));

                log.info("[WS-Auth] User connected: userId={}, nickname={}", userId, nickname);
            } catch (JWTVerificationException e) {
                log.warn("[WS-Auth] JWT verification failed: {}", e.getMessage());
                throw new IllegalArgumentException("认证令牌无效或已过期");
            }
        }
        // AI-GENERATED AIDEV-NOTE: 用户只能订阅自身回复队列，禁止绕过 /user 直接访问 broker。
        if (accessor != null && StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            String destination = accessor.getDestination();
            if (accessor.getUser() == null || destination == null
                    || !destination.matches("/user/queue/rsp(?:/(?:stream|done|error))?")) {
                throw new IllegalArgumentException("无权订阅该消息队列");
            }
        }
        if (accessor != null && StompCommand.SEND.equals(accessor.getCommand())
                && (accessor.getUser() == null || !"/app/chat.send".equals(accessor.getDestination()))) {
            throw new IllegalArgumentException("无权向该地址发送消息");
        }
        return message;
    }

    /**
     * 简单的 Principal 实现
     */
    public record WsPrincipal(String name, String nickname) implements Principal {
        @Override
        public String getName() {
            return name;
        }
    }
}
