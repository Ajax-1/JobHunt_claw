package com.git.hui.jobclaw.channels;

import com.dingtalk.open.app.api.chatbot.BotReplier;
import com.dingtalk.open.app.api.models.bot.ChatbotMessage;
import com.git.hui.jobclaw.channels.sdk.ChatbotMessageEx;
import com.git.hui.jobclaw.channels.sdk.DingDingSdk;
import com.git.hui.jobclaw.core.agent.models.LlmRspCell;
import com.git.hui.jobclaw.core.apis.context.UserRoleEnum;
import com.git.hui.jobclaw.core.apis.service.IUserService;
import com.git.hui.jobclaw.core.bus.ChannelEventPublisher;
import com.git.hui.jobclaw.core.channel.AbsStreamChannel;
import com.git.hui.jobclaw.core.channel.ChannelConfig;
import com.git.hui.jobclaw.core.channel.ChannelReceiveMessage;
import com.git.hui.jobclaw.core.channel.ChannelRegistry;
import com.git.hui.jobclaw.core.channel.ChannelResponseMessage;
import com.git.hui.jobclaw.core.configuration.ConfigurationManager;
import com.git.hui.jobclaw.core.utils.SpringUtil;
import com.git.hui.jobclaw.core.utils.files.ChannelStorageHelper;
import com.git.hui.jobclaw.core.utils.json.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.core.io.Resource;
import org.springframework.util.CollectionUtils;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * 1v1 的钉钉机器人通道，适用于每个用户配置自己的机器人场景
 *
 * @date 2026/4/13
 */
@Slf4j
public class DingDingBotChannel extends AbsStreamChannel<ChatbotMessageEx> {

    private final DingDingBotProperties dingDingBotProperties;

    private final ChannelStorageHelper localStorageHelper;
    // key = robotId, value = SDK
    private Map<String, DingDingSdk> sdkMap = new ConcurrentHashMap<>();

    public DingDingBotChannel(Resource agentWorkspace,
                              ChannelRegistry channelRegistry,
                              ChannelEventPublisher channelEventPublisher,
                              DingDingBotProperties dingDingBotProperties,
                              ConfigurationManager configurationManager, ChannelStorageHelper localStorageHelper) {
        super(agentWorkspace, channelRegistry, channelEventPublisher, configurationManager);
        this.dingDingBotProperties = dingDingBotProperties;
        this.localStorageHelper = localStorageHelper;
    }

    @Override
    public ChannelConfig.ChannelEnum channel() {
        return ChannelConfig.ChannelEnum.DING_DING;
    }

    @Override
    public void activeChannelAccounts() {
        log.info("[DingDing] Start to active all channel accounts....");
        if (dingDingBotProperties.isEnabled() && !CollectionUtils.isEmpty(dingDingBotProperties.getAccounts())) {
            // 虚拟线程的方式进行初始化，加快应用启动速度
            Thread.ofVirtual().start(() -> {
                this.dingDingBotProperties.getAccounts().forEach((k, v) -> {
                    if (!CollectionUtils.isEmpty(v)) {
                        v.forEach(tmp -> registerMsgListenerCallback(k, tmp));
                    }
                });
                channelRegistry.registerChannel(this);
            });
        }
    }


    /**
     * 注册钉钉消息监听器，用于接收钉钉机器人接收到的消息
     *
     * @param robotOwnerUserId 全局用户ID
     * @param config       渠道配置
     */
    private void registerMsgListenerCallback(String robotOwnerUserId, ChannelConfig config) {
        try {
            if (config.getState() == ChannelConfig.ChannelState.ERROR) {
                log.warn("[DingDing] Ignore to start DingDing bot channel for user: {}", robotOwnerUserId);
                return;
            }

            // 首先判断机器人类型
            config.setOwnerJobClawUserId(robotOwnerUserId);

            var sdk = new DingDingSdk((DingDingBotProperties.DingDingBotAccount) config);
            sdkMap.put(config.getAppId(), sdk);
            sdk.start(chatbotMessage -> {
                // 接收人
                var dingDing = chatbotMessage.getSenderStaffId();

                String aiCardId = aiCardStatus.getActiveAiCard(config.getAppId(), dingDing);
                if (StringUtils.isBlank(aiCardId)) {
                    aiCardId = sdk.initStreamAiCardId(config.getAppId(), chatbotMessage);
                    aiCardStatus.startAiCard(config.getAppId(), dingDing, aiCardId);
                }
                ChatbotMessageEx msgEx = new ChatbotMessageEx();
                BeanUtils.copyProperties(chatbotMessage, msgEx);
                msgEx.setAiCardId(aiCardId);
                msgEx.setRobotId(config.getAppId());
                processMessage(MsgWrapper.<ChatbotMessageEx>builder().msg(msgEx).jobClawUserId(robotOwnerUserId).build());
            });

            // 初始化时，只给机器人的拥有者注入心跳，用于离线后重启的主动通知；对于使用者，需要对话之后，才注入心跳信息
            this.channelRegistry.refreshChannelHeartBeatInfoIgnoreNull(robotOwnerUserId, name(), buildHeartBeatCallback(robotOwnerUserId));
            log.info("[DingDing] DingDing bot channel started for user: {} - {}", robotOwnerUserId, config.getAppId());
        } catch (Exception e) {
            log.error("[DingDing] Failed to start DingDing bot channel for user: {}", robotOwnerUserId, e);
            throw new RuntimeException("Failed to initialize DingDing channel", e);
        }
    }


    @Override
    public ChannelReceiveMessage adaptToReceive(MsgWrapper<ChatbotMessageEx> msgWrapper) {
        if (msgWrapper == null) {
            return null;
        }
        ChatbotMessageEx msg = msgWrapper.getMsg();
        String dingDingId = msg.getSenderStaffId();

        // 查询真实用户的id
        String robotOwnerUserId = msgWrapper.getJobClawUserId();
        String robotId = msg.getRobotId();
        var sdk = sdkMap.get(robotId);
        String jobClawUserId = null;
        ChannelConfig channelConfig = sdk.getDingDingBotAccount();
        var user = SpringUtil.getBean(IUserService.class).getUser(dingDingId, channel());
        if (channelConfig.getScope() == ChannelConfig.ChannelScope.OWNER) {
            // 仅作者才能使用
            if (user == null || !String.valueOf(user.userId()).equals(robotOwnerUserId)) {
                errorResponse(msg, "这个机器人只为创作者本人服务哦~，若您是机器人的拥有者，请到个人中心->渠道配置->钉钉机器人->绑定钉钉账号: " + dingDingId);
                return null;
            }
            jobClawUserId = robotOwnerUserId;
        } else if (channelConfig.getScope() == ChannelConfig.ChannelScope.LOGIN) {
            if (user == null) {
                log.warn("[DingDing] Failed to find user for staffId: {}", dingDingId);
                errorResponse(msg, "您的个人JobClaw还没有绑定钉钉渠道哦，请到个人中心->渠道配置->钉钉机器人->绑定钉钉账号: " + dingDingId);
                return null;
            }
            jobClawUserId = String.valueOf(user.userId());
        } else if (channelConfig.getScope() == ChannelConfig.ChannelScope.VIP) {
            if (user == null) {
                errorResponse(msg, "这个机器人属于VIP专享哦~，请先到个人中心->渠道配置->钉钉机器人->绑定钉钉账号: " + dingDingId);
                return null;
            } else if (user.role() != UserRoleEnum.VIP && user.role() != UserRoleEnum.ADMIN) {
                errorResponse(msg, "这个机器人属于VIP专享哦~，您可以到JobClaw开通VIP既可畅享对话");
            }
            jobClawUserId = String.valueOf(user.userId());
        } else if (channelConfig.getScope() == ChannelConfig.ChannelScope.PUBLIC) {
            if (user == null) {
                // 公开的所有人都可以访问的场景，对于没有绑定的用户，直接使用钉钉的用户体系
                jobClawUserId = "D-" + dingDingId;
            } else {
                jobClawUserId = String.valueOf(user.userId());
            }
        }

        msgWrapper.setJobClawUserId(jobClawUserId);
        var msgContent = sdkMap.get(msg.getRobotId()).parseContent(msg);
        if (msgContent == null) {
            // 无法解析
            responseToUser(ChannelResponseMessage.builder()
                    .passThrough(Map.of("input", msg))
                    .type(ChannelResponseMessage.ResponseMessageType.TEXT)
                    .jobClawUserId(jobClawUserId)
                    .toUserId(msg.getSenderStaffId())
                    .streamContents(Flux.just(new LlmRspCell(null, "无法解析您的消息，请稍后再试", null)))
                    .build());
            return null;
        }

        ChannelReceiveMessage.MediaMsg media = msgContent.media();
        if (media != null) {
            var tmpFile = localStorageHelper.autoDownloadFile(jobClawUserId, name(), media.getDownUrl(), media.getFileType());
            media.setFilePath(Path.of(tmpFile));
        }
        ChannelReceiveMessage.FileMsg file = msgContent.file();
        if (file != null) {
            var tmpFile = localStorageHelper.autoDownloadFile(jobClawUserId, name(), file.getDownUrl(), file.getFileType());
            file.setFilePath(Path.of(tmpFile));
        }

        return ChannelReceiveMessage.builder()
                .msgId(msg.getMsgId())
                .message(msgContent.content())
                .medias(media == null ? null : List.of(media))
                .files(file == null ? null : List.of(file))
                .fromUserId(dingDingId)
                .jobClawUserId(msgWrapper.getJobClawUserId())
                .channel(name())
                .stream(true)
                .groupTalk(!"1".equals(msg.getConversationType()))
                .passThrough(Map.of("input", msg))
                .build();
    }


    @Override
    public boolean saveHeartBeatConfig(MsgWrapper<ChatbotMessageEx> wrapper, boolean force) {
        var msg = wrapper.getMsg();
        String type = "1".equals(msg.getConversationType()) ? "im" : "group";
        var prefix = buildHeartBeatKey(wrapper.getJobClawUserId(), msg.getRobotId(), type);
        // 如果存在配置，则不进行更新
        if (!force && configurationManager.getProperty(prefix) != null) {
            return false;
        }

        var response = ChannelResponseMessage.builder()
                .jobClawUserId(wrapper.getJobClawUserId())
                .toUserId(msg.getSenderStaffId())
                .type(ChannelResponseMessage.ResponseMessageType.TEXT)
                .passThrough(Map.of("input", msg))
                .build();
        String value = JsonUtil.toStr(response);
        configurationManager.updateProperties(Map.of(prefix, value));
        return true;
    }

    public Function<Object, ChannelResponseMessage> buildHeartBeatCallback(String jobClawUserId) {
        // fixme 群聊的主动推送消息的场景需要考虑如何做支持
        for (String robotId : sdkMap.keySet()) {
            var key = buildHeartBeatKey(jobClawUserId, robotId, "im");
            String value = configurationManager.getProperty(key);
            if (StringUtils.isNotBlank(value)) {
                var response = JsonUtil.toObj(value, ChannelResponseMessage.class);
                var input = response.getPassThrough().get("input");
                if (!(input instanceof ChatbotMessageEx)) {
                    var msg = JsonUtil.toObj(JsonUtil.toStr(input), ChatbotMessageEx.class);
                    msg.setAiCardId(null);
                    response.setPassThrough(Map.of("input", msg));
                } else {
                    ((ChatbotMessageEx) input).setAiCardId(null);
                }

                return object -> {
                    if (object instanceof Flux<?>) {
                        response.setStreamContents((Flux<LlmRspCell>) object);
                    } else {
                        response.setContent(String.valueOf(object));
                    }
                    return response;
                };
            }
        }
        return null;
    }

    private String buildHeartBeatKey(String jobClawUserId, String robotId, String type) {
        return String.format(HEART_BEAT_CONFIG_PREFIX + ".%s.%s", name(), jobClawUserId, robotId, type);
    }

    private void errorResponse(ChatbotMessageEx msg, String errMsg) {
        String dingDingId = msg.getSenderStaffId();
        var res = ChannelResponseMessage
                .builder()
                .jobClawUserId(null)
                .toUserId(dingDingId)
                .type(ChannelResponseMessage.ResponseMessageType.TEXT)
                .streamContents(Flux.just(new LlmRspCell(null, errMsg, null)))
                .passThrough(Map.of("input", msg))
                .build();
        responseToUser(res);
    }

    /**
     * 发送消息到钉钉
     * 该方法会创建一个异步等待机制,等待外部系统返回响应
     *
     * @param msg 响应消息
     * @return 是否发送成功
     */
    @Override
    public boolean responseToUser(ChannelResponseMessage msg) {
        if (msg == null || msg.getToUserId() == null) {
            log.warn("[DingDing] Invalid message or missing toUserId");
            return false;
        }

        ChatbotMessageEx originalMsg = (ChatbotMessageEx) msg.getPassThrough().get("input");
        // 流式返回的场景
        var sdk = sdkMap.get(originalMsg.getRobotId());
        var stream = msg.getStreamContents();
        String cardId = originalMsg.getAiCardId();
        String dingDingId = originalMsg.getSenderStaffId();
        if (stream != null) {
            if (StringUtils.isBlank(cardId)) {
                // 通常是后台主动给用户发送消息的场景
                cardId = aiCardStatus.getActiveAiCard(originalMsg.getRobotId(), dingDingId);
                if (cardId == null) {
                    cardId = sdk.initStreamAiCardId(originalMsg.getRobotId(), originalMsg);
                }
            }

            if (StringUtils.isBlank(cardId)) {
                var content = stream.blockLast();
                return directReply(originalMsg, content.content());
            } else {
                StringBuilder thinking = new StringBuilder();
                StringBuilder content = new StringBuilder();
                StringBuilder toolInfo = new StringBuilder();
                StringBuilder toolResultInfo = new StringBuilder();
                String finalCardId = cardId;
                stream.doOnNext(response -> {
                            log.debug("[DingDing] Received response chunk: {}", response);
                            if (StringUtils.isNotBlank(response.thinking())) {
                                thinking.append(response.thinking());
                            }
                            if (StringUtils.isNotBlank(response.tool())) {
                                toolInfo.append(response.tool()).append("\n");
                                log.info("[DingDing] Tool call detected: {}", response.tool());
                            }
                            if (StringUtils.isNotBlank(response.toolResult())) {
                                toolResultInfo.append(response.toolResult()).append("\n");
                                log.info("[DingDing] Tool result received, length={}", response.toolResult().length());
                            }
                            if (StringUtils.isNotBlank(response.content())) {
                                content.append(response.content());
                            }
                            // 构建展示内容：工具调用信息 + 工具结果 + 正文
                            String displayContent = buildDisplayContent(toolInfo, toolResultInfo, content);
                            sdk.streamUpdate(finalCardId, thinking.toString(), displayContent, false);
                            aiCardStatus.answerAiCard(originalMsg.getRobotId(), dingDingId, finalCardId);
                        })
                        .doOnError(error -> {
                            log.error("[DingDing] Error in stream response for cardId: {}", finalCardId, error);
                            // 发生错误时，标记卡片为结束状态
                            sdk.streamUpdate(finalCardId, thinking.toString(), "抱歉，生成回复时遇到了错误。", true);
                            aiCardStatus.finishAiCard(originalMsg.getRobotId(), dingDingId, finalCardId);
                        })
                        .doOnComplete(() -> {
                            log.info("[DingDing] Stream response completed for cardId: {}, total length: {}", finalCardId,
                                    content.length());
                            // 流式响应完成，标记卡片为结束状态
                            String displayContent = buildDisplayContent(toolInfo, toolResultInfo, content);
                            sdk.streamUpdate(finalCardId, thinking.toString(), displayContent, true);
                            aiCardStatus.finishAiCard(originalMsg.getRobotId(), dingDingId, finalCardId);
                        }).subscribe();
            }
        } else {
            // 非流式返回的场景，直接回复
            String content = msg.getContent();
            if (StringUtils.isBlank(content)) {
                content = "出现故障了~现在暂无返回，请稍后再试";
            }

            if (StringUtils.isBlank(cardId)) {
                cardId = aiCardStatus.getActiveAiCard(originalMsg.getRobotId(), dingDingId);
                if (cardId != null) {
                    sdk.streamUpdate(cardId, "", content, true);
                    aiCardStatus.finishAiCard(originalMsg.getRobotId(), dingDingId, cardId);
                    return true;
                }

                // 如果会话链接有效，那就通过这个回调发送消息即可
                if (directReply(originalMsg, content)) {
                    return true;
                }

                // 后台主动给用户推送消息的场景，主动创建一个AiCard，用于推送消息
                cardId = sdk.initStreamAiCardId(originalMsg.getRobotId(), originalMsg);
                if (cardId == null) {
                    return false;
                }
            }
            sdk.streamUpdate(cardId, "", content, true);
            // 主动结束这个流式卡片，避免被再次更新
            aiCardStatus.finishAiCard(originalMsg.getRobotId(), dingDingId, cardId);
        }
        return true;
    }

    private boolean directReply(ChatbotMessage originalMsg, String content) {
        if (originalMsg.getSessionWebhookExpiredTime() < System.currentTimeMillis()) {
            // 会话已经过期，无法通过这种方式正确返回
            return false;
        }

        try {
            BotReplier.fromWebhook(originalMsg.getSessionWebhook()).replyText(content);
            return true;
        } catch (IOException e) {
            log.error("[DingDing] Failed to reply to DingDing: {}", content, e);
            return false;
        }
    }

    /**
     * 构建展示内容，将工具调用信息和执行结果与正文内容组合展示
     * <p>
     * AIDEV-NOTE: 钉钉AI卡片不支持多字段独立更新，因此将工具信息拼接到正文前部展示
     */
    private String buildDisplayContent(StringBuilder toolInfo, StringBuilder toolResultInfo, StringBuilder content) {
        StringBuilder display = new StringBuilder();

        // 展示工具调用信息
        if (toolInfo.length() > 0) {
            display.append("**🔧 工具调用**\n").append(toolInfo);
        }

        // 展示工具执行结果
        if (toolResultInfo.length() > 0) {
            if (display.length() > 0) display.append("\n");
            display.append("**📋 执行结果**\n").append(toolResultInfo);
        }

        // 展示正文内容
        if (content.length() > 0) {
            if (display.length() > 0) display.append("\n---\n");
            display.append(content);
        }

        return display.toString();
    }

    /**
     * 添加账号并启动监听
     *
     * @param channelConfig 渠道配置
     * @param <T>           配置类型
     */
    @Override
    public <T extends ChannelConfig> void addAccount(T channelConfig) {
        if (channelConfig instanceof DingDingBotProperties.DingDingBotAccount) {
            DingDingBotProperties.DingDingBotAccount conf = (DingDingBotProperties.DingDingBotAccount) channelConfig;
            registerMsgListenerCallback(conf.getOwnerJobClawUserId(), conf);
            channelRegistry.registerChannel(this);
        } else {
            log.warn("[DingDing] Unsupported config type: {}", channelConfig.getClass().getName());
        }
    }
}
