package com.git.hui.jobclaw.channels;

import com.git.hui.jobclaw.core.bus.ChannelEventPublisher;
import com.git.hui.jobclaw.core.channel.ChannelRegistry;
import com.git.hui.jobclaw.core.configuration.ConfigurationManager;
import com.git.hui.jobclaw.core.utils.files.ChannelStorageHelper;
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
@Import(DingDingBotProperties.class)
public class DingDingBotConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "agent.channels.dingding.enabled", havingValue = "true", matchIfMissing = true)
    public DingDingBotChannel dingDingBotChannel(
            @Value("${agent.workspace}") Resource agentWorkspace,
            DingDingBotProperties wxBotProperties,
            ChannelRegistry channelRegistry,
            ChannelEventPublisher channelEventPublisher,
            ConfigurationManager configurationManager,
            ChannelStorageHelper localStorageHelper
    ) {
        return new DingDingBotChannel(agentWorkspace, channelRegistry, channelEventPublisher, wxBotProperties, configurationManager,
                localStorageHelper);
    }
}
