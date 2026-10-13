package com.git.hui.jobclaw.core.channel;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

@Slf4j
public class ChannelRegistry {

    private final Map<String, Channel> channels;
    private String defaultChannelName;

    /**
     * 适配器，用于将后端返回的消息适配成前端的消息（通常会包含channel需要定位消息接收人的必要信息），主要用于后台任务主动给用户推送消息的场景
     */
    private final Map<String, Function<Object, ChannelResponseMessage>> channelResponseAdapters;

    public ChannelRegistry() {
        this.channels = new ConcurrentHashMap<>();
        this.channelResponseAdapters = new ConcurrentHashMap<>();
    }

    public void registerChannel(Channel channel) {
        channels.put(channel.name(), channel);
        if (channels.size() == 1) {
            this.defaultChannelName = channel.name();
        }
    }

    public Channel getChannel(String channelName) {
        if (StringUtils.isBlank(channelName)) {
            return channels.get(defaultChannelName);
        }
        return channels.get(channelName);
    }

    public void refreshChannelHeartBeatInfo(String jobClawUserId, String channelName, Function<Object, ChannelResponseMessage> adapter) {
        String key = jobClawUserId + ":" + channelName;
        channelResponseAdapters.put(key, adapter);
        log.info("[ChannelRegistry] refresh channel heartbeat info, jobClawUserId={}, channelName={}", jobClawUserId, channelName);
    }

    public void refreshChannelHeartBeatInfoIgnoreNull(String jobClawUserId, String channelName, Function<Object, ChannelResponseMessage> adapter) {
        if (adapter == null) {
            return;
        }
        this.refreshChannelHeartBeatInfo(jobClawUserId, channelName, adapter);
    }

    public Function<Object, ChannelResponseMessage> getChannelRspBuilderAdapter(String jobClawUserId, String channelName) {
        String key = jobClawUserId + ":" + channelName;
        return channelResponseAdapters.get(key);
    }
}
