package com.git.hui.jobclaw.channels.websocket;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * WebSocket 通道配置
 */
@Data
@ConfigurationProperties(prefix = "agent.channels.websocket")
public class WebSocketProperties {
    private boolean enabled = true;
    private String allowedOrigins = "*";
}
