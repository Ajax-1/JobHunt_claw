package com.git.hui.jobclaw.gather.service.ai.impl.spark.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * @date 2025/7/29
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "spring.ai.spark")
public class SparkConfig {
    private String baseUrl;

    private String apiKey;

    private SparkChat chat;

    private String openAiBaseUrl;

    /**
     * ture 表示使用OpenAI的client来实现讯飞大模型交互； false 则表示使用自定义的 client 实现讯飞大模型交互
     */
    private Boolean openAiClient;
}
