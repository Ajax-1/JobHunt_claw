package com.git.hui.jobclaw.channels;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

/**
 * 微信ClawBot相关的配置
 * @date 2026/4/8
 */
@Data
@ConfigurationProperties(prefix = "agent.channels.wechat.clawbot")
public class WxChatClawBotProperties {
    private boolean enabled;
    private String baseUrl;
    private String cdnBaseUrl;

    /**
     * key = JobClaw 账号的 userId
     */
    private Map<String, WxClawBotAccount> accounts;
}
