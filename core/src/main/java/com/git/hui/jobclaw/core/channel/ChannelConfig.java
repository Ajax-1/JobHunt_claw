package com.git.hui.jobclaw.core.channel;

import com.git.hui.jobclaw.core.utils.json.StringBaseEnum;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 通道集成的配置信息
 * @date 2026/4/8
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class ChannelConfig {
    private String appId;
    private String appSecret;
    private ConnectionMode mode;
    private ChannelState state;
    private ChannelScope scope;

    /**
     *  JobClaw 的账号
     */
    private String ownerJobClawUserId;

    public enum ConnectionMode {
        WEBSOCKET,
        WEBHOOK,
        LOOP,
        ;
    }

    public enum ChannelState {
        NORMAL,
        ERROR,
        ;
    }

    @Getter
    public enum ChannelEnum {
        DING_DING("dingding"),
        FEI_SHU("feishu"),
        WEXIN_CLAW_BOT("wechat-clawbot"),
        WEBSOCKET("websocket");

        private String channel;

        ChannelEnum(String channel) {
            this.channel = channel;
        }
    }

    @Getter
    @AllArgsConstructor
    public enum ChannelScope implements StringBaseEnum {
        OWNER("OWNER", "机器人的归属者的聊天渠道"),
        LOGIN("LOGIN", "用户登录的聊天渠道"),
        VIP("VIP", "VIP用户可以享受的聊天渠道"),
        PUBLIC("PUBLIC", "所有人都可以接入的聊天渠道"),
        ;

        private String value;
        private String desc;
    }
}
