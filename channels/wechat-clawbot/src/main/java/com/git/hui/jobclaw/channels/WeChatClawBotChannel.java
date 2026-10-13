package com.git.hui.jobclaw.channels;

import com.git.hui.jobclaw.channels.sdk.MessageBuilder;
import com.git.hui.jobclaw.channels.sdk.WeixinSdk;
import com.git.hui.jobclaw.channels.sdk.WeixinTypes;
import com.git.hui.jobclaw.core.bus.ChannelEventPublisher;
import com.git.hui.jobclaw.core.channel.AbsChannel;
import com.git.hui.jobclaw.core.channel.ChannelConfig;
import com.git.hui.jobclaw.core.channel.ChannelReceiveMessage;
import com.git.hui.jobclaw.core.channel.ChannelRegistry;
import com.git.hui.jobclaw.core.channel.ChannelResponseMessage;
import com.git.hui.jobclaw.core.configuration.ConfigurationManager;
import com.git.hui.jobclaw.core.utils.json.JsonUtil;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.util.CollectionUtils;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * WeChat channel using ClawBot API with WeixinSdk.
 * Refactored to use the complete Weixin SDK for all interactions.
 */
public class WeChatClawBotChannel extends AbsChannel<WeixinTypes.WeixinMessage> {

    private static final Logger log = LoggerFactory.getLogger(WeChatClawBotChannel.class);

    // session save path
    private final String stateDir;

    // Media storage directory (under agent workspace)
    private final Path mediaDir;

    private final Map<String, WeixinSdk> accountMap;

    private final WxChatClawBotProperties wxChatClawBotProperties;


    public WeChatClawBotChannel(Resource agentWorkspace,
                                WxChatClawBotProperties wxBotProperties,
                                ChannelRegistry channelRegistry,
                                ChannelEventPublisher channelEventPublisher,
                                ConfigurationManager configurationManager) {
        super(agentWorkspace, channelRegistry, channelEventPublisher, configurationManager);
        this.wxChatClawBotProperties = wxBotProperties;

        // Resolve media directory under workspace
        try {
            Path workspacePath = agentWorkspace.getFile().toPath();
            this.stateDir = workspacePath.resolve("channel").resolve("wx").resolve("state").toString();
            this.mediaDir = workspacePath.resolve("channel").resolve("wx").resolve("media");
        } catch (Exception e) {
            throw new RuntimeException("Failed to resolve workspace path", e);
        }

        this.accountMap = new ConcurrentHashMap<>();
    }

    @Override
    public void activeChannelAccounts() {
        log.info("[WeChatClaw] Start to active all channel accounts....");
        this.loadAllAccounts();
    }

    @Override
    public ChannelConfig.ChannelEnum channel() {
        return ChannelConfig.ChannelEnum.WEXIN_CLAW_BOT;
    }

    /**
     * 加载所有用户，并启动轮询的消息侦听
     */
    public void loadAllAccounts() {
        if (!CollectionUtils.isEmpty(this.wxChatClawBotProperties.getAccounts())) {
            this.channelRegistry.registerChannel(this);
            this.wxChatClawBotProperties.getAccounts().forEach((jobUserId, account) -> {
                if (account.getState() != ChannelConfig.ChannelState.NORMAL || StringUtils.isBlank(account.getAppSecret())) {
                    log.warn("[WeChatClaw] 账号异常，已回略! appId={}, state={}",
                            account.getAppId(),
                            account.getState());
                    return;
                }

                if (StringUtils.isBlank(account.getOwnerJobClawUserId())) {
                    account.setOwnerJobClawUserId(jobUserId);
                }
                this.addAccount(account);
            });
        } else {
            log.warn("[WeChatClaw] WeChat ClawBot not configured (missing bot-token).");
        }
    }

    @Override
    public <T extends ChannelConfig> void addAccount(T account) {
        WxClawBotAccount botAccount = (WxClawBotAccount) account;
        // Initialize WeiXinSdk
        final String botToken = account.getAppSecret();
        final String wxUserId = botAccount.getUserId();
        final String jobClawUserId = botAccount.getOwnerJobClawUserId();
        var sdk = new WeixinSdk.Builder()
                .baseUrl(this.wxChatClawBotProperties.getBaseUrl())
                .cdnBaseUrl(this.wxChatClawBotProperties.getCdnBaseUrl())
                .token(botToken)
                .channelVersion("1.0.3")
                .stateDir(stateDir)
                .mediaDir(mediaDir.resolve(jobClawUserId).toString())
                .accountId(wxUserId)
                .build();
        var lastSdk = this.accountMap.put(wxUserId, sdk);
        if (lastSdk != null) {
            // 账号更新的场景，停止旧的账号；启动新的账号
            log.info("[WeChatClaw] account updated, stop last sdk, userId: {}, lastSdk: {}", wxUserId, lastSdk);
            lastSdk.shutdown();
            log.info("[WeChatClaw] account updated, start new sdk, userId: {}, sdk: {}", wxUserId, sdk);
        } else {
            // 初始化时，基于配置，主动维护心跳信息
            channelRegistry.refreshChannelHeartBeatInfoIgnoreNull(jobClawUserId, name(), this.buildHeartBeatCallback(jobClawUserId));
        }

        // 轮询监听新的账号消息
        sdk.startPolling(new WeixinSdk.MessageHandler() {
            @Override
            public void onMessage(WeixinTypes.WeixinMessage message) {
                try {
                    processMessage(MsgWrapper.<WeixinTypes.WeixinMessage>builder().msg(message).jobClawUserId(jobClawUserId).build());
                } catch (Exception e) {
                    log.error("[WeChatClaw] Error processing message", e);
                }
            }

            @Override
            public void onDisconnect() {
                account.setState(ChannelConfig.ChannelState.ERROR);
            }
        });
        log.info("[WeChatClaw] Started WeChat ClawBot integration with bot token: {}...", botToken.substring(0, Math.min(8, botToken.length())));
    }

    /**
     * Process incoming message using WeixinSdk types
     */
    @Override
    public ChannelReceiveMessage adaptToReceive(MsgWrapper<WeixinTypes.WeixinMessage> msgWrapper) {
        WeixinTypes.WeixinMessage message = msgWrapper.getMsg();
        String fromUser = message.getFromUserId();
        String msgContextToken = message.getContextToken();

        if (fromUser == null || fromUser.isBlank()) {
            log.debug("[WeChatClaw] Skipping message with empty from_user_id");
            return null;
        }


        // Build complete message (text + media)
        List<ChannelReceiveMessage.MediaMsg> medias = new ArrayList<>();
        List<ChannelReceiveMessage.FileMsg> files = new ArrayList<>();

        // Add text if present
        String messageText = MessageBuilder.extractText(message);
        var builder = ChannelReceiveMessage.builder()
                .msgId("WX_" + message.getMessageId())
                .channel(name())
                .fromUserId(message.getFromUserId())
                .jobClawUserId(msgWrapper.getJobClawUserId())
                .passThrough(Map.of("msgContentToken", msgContextToken))
                .message(messageText)
                .files(files)
                .medias(medias);

        // Process media items and collect them
        if (!CollectionUtils.isEmpty(message.getItemList())) {
            for (WeixinTypes.MessageItem item : message.getItemList()) {
                if (item.getImageItem() != null && item.getImageItem().getLocalPath() != null) {
                    medias.add(ChannelReceiveMessage.MediaMsg
                            .builder()
                            .mimeType("image/jpeg")
                            .filePath(item.getImageItem().getLocalPath())
                            .build());
                }
                if (item.getFileItem() != null && item.getFileItem().getLocalPath() != null) {
                    files.add(ChannelReceiveMessage.FileMsg
                            .builder()
                            .fileName(item.getFileItem().getFileName())
                            .filePath(item.getFileItem().getLocalPath())
                            .mimeType("application/octet-stream")
                            .build());
                }
                if (item.getVideoItem() != null && item.getVideoItem().getLocalPath() != null) {
                    medias.add(ChannelReceiveMessage.MediaMsg
                            .builder()
                            .filePath(item.getVideoItem().getLocalPath())
                            .mimeType("video/mp4")
                            .build());
                }
                if (item.getVoiceItem() != null && item.getVoiceItem().getLocalPath() != null) {
                    files.add(ChannelReceiveMessage.FileMsg
                            .builder()
                            .fileName(item.getVoiceItem().getLocalPath().getFileName().toString())
                            .filePath(item.getVoiceItem().getLocalPath())
                            .mimeType("audio/mp3")
                            .build());
                }
            }
        }
        return builder.build();
    }

    private Map<String, Long> lastUpdateHeatBeatTime = new ConcurrentHashMap<>();

    @Override
    public boolean saveHeartBeatConfig(MsgWrapper<WeixinTypes.WeixinMessage> wrapper, boolean force) {
        String key = buildHeartBeatConfig(wrapper.getJobClawUserId());
        long now = System.currentTimeMillis();
        if (!force || now - lastUpdateHeatBeatTime.getOrDefault(key, 0L) < 1000 * 60 * 5) {
            // 五分钟更新一次
            return false;
        }

        var response = ChannelResponseMessage.builder()
                .jobClawUserId(wrapper.getJobClawUserId())
                .toUserId(wrapper.getMsg().getFromUserId())
                .type(ChannelResponseMessage.ResponseMessageType.TEXT)
                .passThrough(Map.of("msgContentToken", wrapper.getMsg().getContextToken()))
                .build();
        String value = JsonUtil.toStr(response);
        configurationManager.updateProperties(Map.of(key, value));
        lastUpdateHeatBeatTime.put(key, now);
        return true;
    }

    @Override
    public Function<Object, ChannelResponseMessage> buildHeartBeatCallback(String jobClawUserId) {
        String value = configurationManager.getProperty(buildHeartBeatConfig(jobClawUserId));
        if (StringUtils.isBlank(value)) {
            return null;
        }
        var rsp = JsonUtil.toObj(value, ChannelResponseMessage.class);
        return input -> {
            rsp.setContent(String.valueOf(input));
            return rsp;
        };
    }

    private String buildHeartBeatConfig(String jobClawUserId) {
        return String.format(HEART_BEAT_CONFIG_PREFIX, name(), jobClawUserId);
    }

    /**
     * Shutdown the SDK
     */
    public void shutdown() {
        accountMap.values().forEach(WeixinSdk::shutdown);
    }


    // todo 这里现在只实现了发送文本消息，对于图片、文件、视频、语音等消息，待进一步实现
    @Override
    public boolean responseToUser(ChannelResponseMessage message) {
        var weixinSdk = this.accountMap.get(message.getToUserId());
        if (weixinSdk == null) {
            log.error("WeixinSdk not initialized");
            return false;
        }

        try {
            // 微信的ClawBot不支持主动发送消息，需要通过问询中的 contextToken 来回复
            // Get context token from manager
            String contextToken = (String) message.getPassThrough().get("msgContentToken");
            if (contextToken != null) {
                weixinSdk.getMessageSender().sendTextMessage(message.getToUserId(), message.getContent(), contextToken);
            }
            log.debug("[WeChatClaw] Message send: {}", message);
            return true;
        } catch (Exception e) {
            log.error("[WeChatClaw] Failed to send message: {}", message, e);
            return false;
        }
    }
}
