package com.git.hui.jobclaw.core.channel;

import com.git.hui.jobclaw.core.bus.ChannelEventPublisher;
import com.git.hui.jobclaw.core.configuration.ConfigurationManager;
import com.git.hui.jobclaw.core.utils.ThrowableUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;

import java.util.function.Function;

/**
 * 抽象通道
 * @date 2026/4/8
 */
@Order(100)
@Slf4j
public abstract class AbsChannel<T> implements Channel, ChannelMsgAdapter<T>, CommandLineRunner {
    // 第一个中间参数为 channelName，第二个参数为 JobClawUserId
    protected final String HEART_BEAT_CONFIG_PREFIX = "agent.channels.%s.heartbeat.%s";
    protected final ChannelEventPublisher channelEventPublisher;
    protected final ChannelRegistry channelRegistry;

    protected final Resource agentWorkspace;

    protected final ConfigurationManager configurationManager;

    public AbsChannel(@Value("${agent.workspace}") Resource agentWorkspace,
                      ChannelRegistry channelRegistry,
                      ChannelEventPublisher channelEventPublisher,
                      ConfigurationManager configurationManager) {
        this.agentWorkspace = agentWorkspace;
        this.channelEventPublisher = channelEventPublisher;
        this.channelRegistry = channelRegistry;
        this.configurationManager = configurationManager;
    }

    public void processMessage(MsgWrapper<T> msg) {
        var r = adaptToReceive(msg);
        if (r == null) {
            return;
        }

        try {
            var tag = this.saveHeartBeatConfig(msg, channelRegistry.getChannelRspBuilderAdapter(r.getJobClawUserId(), r.getChannel()) == null);
            if (tag) {
                // 基于用户主动发起的对话，自动更新心跳信息，便于后台主动推送消息给用户
                var func = buildHeartBeatCallback(msg.getJobClawUserId());
                channelRegistry.refreshChannelHeartBeatInfoIgnoreNull(msg.getJobClawUserId(), r.getChannel(), func);
            }
            reportToAgent(r);
        } catch (Exception e) {
            ChannelResponseMessage response = ChannelResponseMessage.builder()
                    .jobClawUserId(msg.getJobClawUserId())
                    .toUserId(r.getFromUserId())
                    .type(ChannelResponseMessage.ResponseMessageType.TEXT)
                    .content("系统异常，请稍后再试\n" + ThrowableUtil.getStackTrace(e))
                    .passThrough(r.getPassThrough())
                    .build();
            responseToUser(response);
        }
    }

    @Override
    public void reportToAgent(ChannelReceiveMessage msg) {
        if (StringUtils.isBlank(msg.getChannel())) {
            msg.setChannel(name());
        }
        channelEventPublisher.publishMessageReceived(msg.getChannel(), msg, true);
    }

    /**
     * 保存Channel会话的心跳信息，用于后台主动给用户推送消息
     * @param wrapper
     * @param force
     * @return true 表示保存成功，false 表示无需保存或者保存失败
     */
    public abstract boolean saveHeartBeatConfig(MsgWrapper<T> wrapper, boolean force);

    /**
     * 构建后台主动给用户推送消息的回调函数
     * @param jobClawUserId
     * @return
     */
    public abstract Function<Object, ChannelResponseMessage> buildHeartBeatCallback(String jobClawUserId);

    @Override
    public void run(String... args) throws Exception {
        activeChannelAccounts();
    }

    public abstract void activeChannelAccounts();
}
