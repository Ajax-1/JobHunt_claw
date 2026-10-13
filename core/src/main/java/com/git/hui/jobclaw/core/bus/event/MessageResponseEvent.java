package com.git.hui.jobclaw.core.bus.event;

import com.git.hui.jobclaw.core.channel.ChannelResponseMessage;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.time.Instant;

/**
 * 消息响应事件 - 当系统准备回复用户消息时触发
 * AIDEV-NOTE: 用于异步消息推送和主动消息发送场景
 *
 * @date 2026/4/8
 */
@Getter
public class MessageResponseEvent extends ApplicationEvent {

    /**
     * 响应ID
     */
    private final String responseId;

    /**
     * 关联的消息ID（如果是回复消息）
     */
    private final String relatedMessageId;

    /**
     * 目标通道
     */
    private final String channel;

    /**
     * 响应消息内容
     */
    private final ChannelResponseMessage responseMessage;

    /**
     * 创建时间戳
     */
    private final Instant createdAt;

    /**
     * 是否为主动消息（非用户消息触发）
     */
    private final boolean isProactive;

    /**
     * 优先级（数字越小优先级越高）
     */
    private final int priority;

    public MessageResponseEvent(Object source, String responseId, String relatedMessageId,
                                String channelName, ChannelResponseMessage responseMessage,
                                boolean isProactive, int priority) {
        super(source);
        this.responseId = responseId;
        this.relatedMessageId = relatedMessageId;
        this.channel = channelName;
        this.responseMessage = responseMessage;
        this.createdAt = Instant.now();
        this.isProactive = isProactive;
        this.priority = priority;
    }

    /**
     * 创建回复消息事件（针对用户消息的回复）
     */
    public static MessageResponseEvent reply(Object source, String responseId,
                                             String relatedMessageId, String channelName, ChannelResponseMessage responseMessage) {
        return new MessageResponseEvent(source, responseId, relatedMessageId, channelName, responseMessage, false, 5);
    }

    /**
     * 创建主动推送消息事件
     */
    public static MessageResponseEvent proactive(Object source, String responseId,
                                                 String channelName,
                                                 ChannelResponseMessage responseMessage,
                                                 int priority) {
        return new MessageResponseEvent(source, responseId, null, channelName, responseMessage, true, priority);
    }
}
