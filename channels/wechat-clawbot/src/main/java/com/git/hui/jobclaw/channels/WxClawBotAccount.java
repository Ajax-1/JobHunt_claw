package com.git.hui.jobclaw.channels;

import com.git.hui.jobclaw.core.channel.ChannelConfig;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 微信 ClawBot 账号配置
 * @date 2026/4/8
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class WxClawBotAccount extends ChannelConfig {
    /**
     * 微信 ClawBot 用户ID
     */
    private String userId;
}
