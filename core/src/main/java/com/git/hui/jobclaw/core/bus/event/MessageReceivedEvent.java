package com.git.hui.jobclaw.core.bus.event;

import com.git.hui.jobclaw.core.channel.ChannelReceiveMessage;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.time.Instant;

/**
 * 消息接收事件 - 当从通道接收到用户消息时触发
 * AIDEV-NOTE: 事件驱动架构核心事件类，所有消息处理都基于此事件
 *
 * @date 2026/4/8
 */
@Getter
public class MessageReceivedEvent extends ApplicationEvent {

    /**
     * 消息ID（用于追踪和去重）
     */
    private final String messageId;

    /**
     * 原始消息内容
     */
    private final ChannelReceiveMessage originalMessage;

    /**
     * 消息接收时间戳
     */
    private final Instant receivedAt;

    /**
     * 是否需要回复
     */
    private final boolean needReply;

    public MessageReceivedEvent(Object source, String messageId, ChannelReceiveMessage originalMessage,
                                boolean needReply) {
        super(source);
        this.messageId = messageId;
        this.originalMessage = originalMessage;
        this.receivedAt = Instant.now();
        this.needReply = needReply;
    }
}
