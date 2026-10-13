package com.git.hui.jobclaw.core.bus.event;

import com.git.hui.jobclaw.core.channel.ChannelConfig;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.time.Instant;

/**
 * 用户移除事件 - 移除用户的Channel监听
 *
 * @date 2026/4/8
 */
@Getter
public class UserDisconnectedEvent extends ApplicationEvent {
    private final String channel;

    /**
     * 通道用户信息
     */
    private final ChannelConfig channelUser;

    /**
     * 连接时间戳
     */
    private final Instant disconnectedAt;

    private final String reason;

    public UserDisconnectedEvent(Object source, String channel, ChannelConfig channelUser, String reason) {
        super(source);
        this.channel = channel;
        this.channelUser = channelUser;
        this.disconnectedAt = Instant.now();
        this.reason = reason;
    }
}
