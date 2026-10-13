package com.git.hui.jobclaw.core.bus.event;

import com.git.hui.jobclaw.core.channel.ChannelConfig;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.time.Instant;

/**
 * 用户连接事件 - 当新用户首次接入通道时触发
 * AIDEV-NOTE: 用于用户自动注册、欢迎消息发送等场景
 *
 * @date 2026/4/8
 */
@Getter
public class UserConnectedEvent extends ApplicationEvent {

    private final String channel;
    /**
     * 三方外部userId，用于推送通道消息时，指定接收人
     */
    private final String userId;
    /**
     * 通道用户信息
     */
    private final ChannelConfig channelUser;

    /**
     * 连接时间戳
     */
    private final Instant connectedAt;

    /**
     * 是否为新用户（首次接入）
     */
    private final boolean isNewUser;

    /**
     * 来源IP（如果可用）
     */
    private final String sourceIp;

    public UserConnectedEvent(Object source, String channel, String fromUserId, ChannelConfig channelUser, boolean isNewUser, String sourceIp) {
        super(source);
        this.channel = channel;
        this.userId = fromUserId;
        this.channelUser = channelUser;
        this.connectedAt = Instant.now();
        this.isNewUser = isNewUser;
        this.sourceIp = sourceIp;
    }
}
