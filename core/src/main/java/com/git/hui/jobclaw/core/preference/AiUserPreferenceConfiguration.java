package com.git.hui.jobclaw.core.preference;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * AI 模型配置自动装配
 * @date 2026/4/9
 */
@Configuration
@ComponentScan("com.git.hui.jobclaw.core.providers")
@EnableConfigurationProperties(AiUserPreferenceProperties.class)
public class AiUserPreferenceConfiguration {
}
