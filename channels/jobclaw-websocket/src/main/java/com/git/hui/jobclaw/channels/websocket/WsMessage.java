package com.git.hui.jobclaw.channels.websocket;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 前端通过 WebSocket 发送的消息体
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WsMessage {
    private String msgId;
    private String message;
    private List<String> files;
    private List<String> medias;
}
