package com.git.hui.jobclaw.channels.websocket;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * WebSocket 响应消息格式
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WsResponseMessage {
    private String msgId;
    private String type; // "text", "stream", "stream_done", "error"
    private String content;

    // 流式 chunk 数据
    private String thinking;
    private String tool;
    private String toolResult;

    public static WsResponseMessage text(String msgId, String content) {
        return WsResponseMessage.builder()
                .msgId(msgId)
                .type("text")
                .content(content)
                .build();
    }

    public static WsResponseMessage streamChunk(String msgId, String thinking, String content, String tool, String toolResult) {
        return WsResponseMessage.builder()
                .msgId(msgId)
                .type("stream")
                .thinking(thinking)
                .content(content)
                .tool(tool)
                .toolResult(toolResult)
                .build();
    }

    public static WsResponseMessage streamDone(String msgId) {
        return WsResponseMessage.builder()
                .msgId(msgId)
                .type("stream_done")
                .build();
    }

    public static WsResponseMessage error(String msgId, String message) {
        return WsResponseMessage.builder()
                .msgId(msgId)
                .type("error")
                .content(message)
                .build();
    }
}
