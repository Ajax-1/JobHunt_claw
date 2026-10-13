package com.git.hui.jobclaw.core.channel;

/**
 *
 * @date 2026/4/8
 */
public interface Channel {
    default String name() {
        return channel().getChannel();
    }

    ChannelConfig.ChannelEnum channel();

    /**
     * 通道接收到消息，并向外发送给Agent
     *
     * @return
     */
    void reportToAgent(ChannelReceiveMessage msg);

    /**
     * 向通道发送消息
     *
     * @param msg
     * @return
     */
    boolean responseToUser(ChannelResponseMessage msg);


    /**
     * 新增一个用户，即 一个通道，支持多个用户进行沟通对话
     *
     * @param channelConfig
     */
    default <T extends ChannelConfig> void addAccount(T channelConfig) {
        throw new UnsupportedOperationException("不支持添加用户");
    }
}
