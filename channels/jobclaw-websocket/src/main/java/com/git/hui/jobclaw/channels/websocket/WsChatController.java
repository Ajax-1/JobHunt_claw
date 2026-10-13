package com.git.hui.jobclaw.channels.websocket;

import com.git.hui.jobclaw.core.channel.ChannelMsgAdapter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.UUID;

/**
 * STOMP 消息控制器
 * <p>
 * 接收前端通过 STOMP 发送的聊天消息，交给 WebSocketChannel.processMessage() 处理，
 * 走标准的 Channel 消息流程：adaptToReceive → reportToAgent → MsgRouter → responseToUser。
 */
@Slf4j
@Controller
public class WsChatController {

    @Autowired
    private WebSocketChannel webSocketChannel;

    /**
     * 处理前端发送的聊天消息
     * 客户端发送到 /app/chat.send，路由到此方法
     */
    @MessageMapping("/chat.send")
    public void handleChatMessage(
            @Payload WsMessage wsMessage,
            SimpMessageHeaderAccessor headerAccessor,
            Principal principal) {

        if (principal == null) {
            log.warn("[WS] Received message without principal");
            return;
        }

        String userId = principal.getName();
        log.debug("[WS] Received message from user {}: {}", userId, wsMessage.getMessage());

        // 确保消息有 ID
        if (wsMessage.getMsgId() == null || wsMessage.getMsgId().isBlank()) {
            wsMessage.setMsgId("WS_MSG_" + UUID.randomUUID());
        }

        // 交给 WebSocketChannel 走标准 Channel 流程
        // processMessage → adaptToReceive → reportToAgent → MsgRouter
        webSocketChannel.processMessage(
                ChannelMsgAdapter.MsgWrapper.<WsMessage>builder()
                        .jobClawUserId(userId)
                        .msg(wsMessage)
                        .build()
        );
    }
}
