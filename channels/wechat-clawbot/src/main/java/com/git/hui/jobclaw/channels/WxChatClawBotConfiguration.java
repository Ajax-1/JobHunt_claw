package com.git.hui.jobclaw.channels;

import com.git.hui.jobclaw.core.bus.ChannelEventPublisher;
import com.git.hui.jobclaw.core.channel.ChannelRegistry;
import com.git.hui.jobclaw.core.configuration.ConfigurationManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.Resource;

/**
 * 微信 ClawBot 通道配置
 * @date 2026/4/8
 */
@AutoConfiguration
@Import(WxChatClawBotProperties.class)
public class WxChatClawBotConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "agent.channels.wechat.clawbot.enabled", havingValue = "true", matchIfMissing = true)
    public WeChatClawBotChannel weClawBotChannel(
            @Value("${agent.workspace}") Resource agentWorkspace,
            WxChatClawBotProperties wxBotProperties,
            ChannelRegistry channelRegistry,
            ChannelEventPublisher channelEventPublisher,
            ConfigurationManager configurationManager
    ) {
        return new WeChatClawBotChannel(agentWorkspace, wxBotProperties, channelRegistry, channelEventPublisher, configurationManager);
    }
}
