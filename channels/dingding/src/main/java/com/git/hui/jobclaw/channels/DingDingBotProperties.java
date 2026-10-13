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
 * @date 2026/4/13
 */
@Data
@ConfigurationProperties(prefix = "agent.channels.dingding")
public class DingDingBotProperties {
    private boolean enabled;
    private Map<String, List<DingDingBotAccount>> accounts;


    @Data
    @SuperBuilder
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode(callSuper = true)
    public static class DingDingBotAccount extends ChannelConfig {
        /**
         * 机器人昵称
         */
        private String botName;
        /**
         * 用于流式返回的 cardId
         */
        private String aiCardId;
    }
}
