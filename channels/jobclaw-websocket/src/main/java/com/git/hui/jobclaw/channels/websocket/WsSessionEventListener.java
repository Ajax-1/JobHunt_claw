package com.git.hui.jobclaw.channels.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

/**
 * WebSocket 会话事件监听器
 * <p>
 * 跟踪 WebSocket 连接/断开事件，维护会话映射。
 */
@Slf4j
public class WsSessionEventListener {

    private final WsSessionManager sessionManager;

    public WsSessionEventListener(WsSessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    @EventListener
    public void handleWebSocketConnect(SessionConnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        Object principal = accessor.getUser();
        if (principal instanceof WsAuthChannelInterceptor.WsPrincipal wsPrincipal) {
            String userId = wsPrincipal.name();
            String sessionId = accessor.getSessionId();
            sessionManager.registerSession(userId, sessionId);
            log.info("[WS-Event] User connected: userId={}, sessionId={}", userId, sessionId);
        }
    }

    @EventListener
    public void handleWebSocketDisconnect(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = accessor.getSessionId();
        sessionManager.removeSession(sessionId);
        log.info("[WS-Event] Session disconnected: sessionId={}", sessionId);
    }
}
