package com.git.hui.jobclaw.channels.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket 会话管理器
 * <p>
 * 管理活跃的 WebSocket 连接，提供按用户发送消息的能力。
 */
@Slf4j
@Component
public class WsSessionManager {

    private final SimpMessagingTemplate messagingTemplate;

    /**
     * userId → set of session principal names
     */
    private final Map<String, Set<String>> userSessions = new ConcurrentHashMap<>();

    /**
     * principal name → userId
     */
    private final Map<String, String> principalUserMap = new ConcurrentHashMap<>();

    public WsSessionManager(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void registerSession(String userId, String sessionId) {
        userSessions.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(sessionId);
        principalUserMap.put(sessionId, userId);
        log.info("[WS] Session registered: userId={}, sessionId={}, total={}",
                userId, sessionId, userSessions.get(userId).size());
    }

    public void removeSession(String sessionId) {
        String userId = principalUserMap.remove(sessionId);
        if (userId != null) {
            var sessions = userSessions.get(userId);
            if (sessions != null) {
                sessions.remove(sessionId);
                if (sessions.isEmpty()) {
                    userSessions.remove(userId);
                }
            }
            log.info("[WS] Session removed: userId={}, sessionId={}", userId, sessionId);
        }
    }

    public String getUserIdBySession(String sessionId) {
        return principalUserMap.get(sessionId);
    }

    public boolean hasActiveSession(String userId) {
        var sessions = userSessions.get(userId);
        return sessions != null && !sessions.isEmpty();
    }

    public void sendToUser(String userId, String destination, Object payload) {
        messagingTemplate.convertAndSendToUser(userId, destination, payload);
    }

    public void sendToPrincipal(String principalName, String destination, Object payload) {
        messagingTemplate.convertAndSendToUser(principalName, destination, payload);
    }

    public String getUserIdFromPrincipal(Principal principal) {
        if (principal == null) return null;
        return principal.getName();
    }
}
