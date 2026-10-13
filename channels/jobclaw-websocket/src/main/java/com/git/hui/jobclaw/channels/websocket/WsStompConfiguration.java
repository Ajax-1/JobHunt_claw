package com.git.hui.jobclaw.channels.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Slf4j
@Configuration
@EnableWebSocketMessageBroker
public class WsStompConfiguration implements WebSocketMessageBrokerConfigurer {

    private final WebSocketProperties properties;
    private final WsAuthChannelInterceptor authInterceptor;

    public WsStompConfiguration(WebSocketProperties properties, @Lazy WsAuthChannelInterceptor authInterceptor) {
        this.properties = properties;
        this.authInterceptor = authInterceptor;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // AI-GENERATED AIDEV-NOTE: /user 由用户路由解析，broker 接收解析后的 /queue。
        config.enableSimpleBroker("/topic", "/queue");
        config.setApplicationDestinationPrefixes("/app");
        config.setUserDestinationPrefix("/user");
        // AI-GENERATED AIDEV-NOTE: 保证流式文本和完成事件按发布顺序抵达，避免断句错乱。
        config.setPreservePublishOrder(true);
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(authInterceptor);
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        String[] origins = properties.getAllowedOrigins() != null
                ? new String[]{properties.getAllowedOrigins()}
                : new String[]{"*"};
        registry.addEndpoint("/ws").setAllowedOrigins(origins);
        log.info("[WebSocket] STOMP endpoint registered at /ws, allowedOrigins={}", properties.getAllowedOrigins());
    }

    @Bean
    public WsAuthChannelInterceptor wsAuthChannelInterceptor(
            @Value("${jobclaw.jwt.secret:jobclaw}") String jwtSecret,
            @Value("${jobclaw.jwt.issuer:jobclaw}") String jwtIssuer) {
        return new WsAuthChannelInterceptor(jwtSecret, jwtIssuer);
    }
}
