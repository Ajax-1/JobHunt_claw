package com.git.hui.jobclaw.channels.websocket;

import com.git.hui.jobclaw.core.bus.ChannelEventPublisher;
import com.git.hui.jobclaw.core.channel.ChannelRegistry;
import com.git.hui.jobclaw.core.configuration.ConfigurationManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.Resource;

/**
 * WebSocket 通道 Bean 定义
 * <p>
 * STOMP/WebSocket 端点和认证拦截器由 {@link WsStompConfiguration} 负责。
 */
@Slf4j
@AutoConfiguration
@ConditionalOnProperty(name = "agent.channels.websocket.enabled", havingValue = "true", matchIfMissing = true)
@Import({WebSocketProperties.class, WsStompConfiguration.class})
public class WebSocketConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public WsSessionEventListener wsSessionEventListener(WsSessionManager sessionManager) {
        return new WsSessionEventListener(sessionManager);
    }

    @Bean
    @ConditionalOnMissingBean
    public WebSocketChannel webSocketChannel(
            @Value("${agent.workspace}") Resource agentWorkspace,
            ChannelRegistry channelRegistry,
            ChannelEventPublisher channelEventPublisher,
            ConfigurationManager configurationManager,
            WsSessionManager sessionManager,
            WebSocketProperties properties) {
        return new WebSocketChannel(agentWorkspace, channelRegistry, channelEventPublisher,
                configurationManager, sessionManager, properties);
    }
}
