package com.git.hui.jobclaw.channels;

import com.git.hui.jobclaw.core.channel.ChannelConfig;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Map;

/**
 *
 * @date 2026/4/16
 */
@Data
@ConfigurationProperties(prefix = "agent.channels.feishu")
public class FeiShuBotProperties {

    private boolean enabled;
    private Map<String, List<FeiShuBotAccount>> accounts;

    @Data
    @SuperBuilder
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode(callSuper = true)
    public static class FeiShuBotAccount extends ChannelConfig {
        private String botName;
        private boolean stream;
    }
}
