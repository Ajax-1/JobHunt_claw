package com.git.hui.jobclaw.channels;

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
import com.git.hui.jobclaw.core.utils.MimeUtils;
import com.git.hui.jobclaw.core.utils.SpringUtil;
import com.git.hui.jobclaw.core.utils.files.ChannelStorageHelper;
import com.git.hui.jobclaw.core.utils.json.JsonUtil;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.lark.oapi.core.utils.Jsons;
import com.lark.oapi.event.EventDispatcher;
import com.lark.oapi.service.cardkit.v1.model.ContentCardElementReq;
import com.lark.oapi.service.cardkit.v1.model.ContentCardElementReqBody;
import com.lark.oapi.service.cardkit.v1.model.ContentCardElementResp;
import com.lark.oapi.service.cardkit.v1.model.CreateCardReq;
import com.lark.oapi.service.cardkit.v1.model.CreateCardReqBody;
import com.lark.oapi.service.cardkit.v1.model.CreateCardResp;
import com.lark.oapi.service.cardkit.v1.model.SettingsCardReq;
import com.lark.oapi.service.cardkit.v1.model.SettingsCardReqBody;
import com.lark.oapi.service.cardkit.v1.model.SettingsCardResp;
import com.lark.oapi.service.im.ImService;
import com.lark.oapi.service.im.v1.model.CreateMessageReq;
import com.lark.oapi.service.im.v1.model.CreateMessageReqBody;
import com.lark.oapi.service.im.v1.model.CreateMessageResp;
import com.lark.oapi.service.im.v1.model.GetMessageResourceReq;
import com.lark.oapi.service.im.v1.model.GetMessageResourceResp;
import com.lark.oapi.service.im.v1.model.P1MessageReadV1;
import com.lark.oapi.service.im.v1.model.P2MessageReceiveV1;
import com.lark.oapi.ws.Client;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.io.Resource;
import org.springframework.util.CollectionUtils;
import reactor.core.publisher.Flux;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * 飞书机器人通道（基于飞书OpenAPI 2.5.3）
 * https://open.feishu.cn/document/server-side-sdk/java-sdk-guide/handle-events
 * @date 2026/4/13
 */
@Slf4j
public class FeiShuBotChannel extends AbsStreamChannel<FeiShuBotChannel.ChatbotMessageEx> {

    private final FeiShuBotProperties FeiShuBotProperties;
    private final ChannelStorageHelper channelStorageHelper;

    /**
     * key = robotId, value = 构建AICard用于流式返回的卡片管理
     */
    private final Map<String, StreamCardManager> cardManagers = new ConcurrentHashMap<>();

    public FeiShuBotChannel(Resource agentWorkspace,
                            ChannelRegistry channelRegistry,
                            ChannelEventPublisher channelEventPublisher,
                            FeiShuBotProperties FeiShuBotProperties,
                            ConfigurationManager configurationManager, ChannelStorageHelper channelStorageHelper) {
        super(agentWorkspace, channelRegistry, channelEventPublisher, configurationManager);
        this.FeiShuBotProperties = FeiShuBotProperties;
        this.channelStorageHelper = channelStorageHelper;
    }

    @Override
    public void activeChannelAccounts() {
        log.info("[FeiShu] Start to active all channel accounts....");
        if (FeiShuBotProperties.isEnabled() && !CollectionUtils.isEmpty(FeiShuBotProperties.getAccounts())) {
            // 虚拟线程初始化，提升启动速度
            Thread.ofVirtual().start(() -> {
                FeiShuBotProperties.getAccounts().forEach((jobClawUserId, accounts) -> {
                    if (!CollectionUtils.isEmpty(accounts)) {
                        accounts.forEach(account -> registerMsgListenerCallback(jobClawUserId, account));
                    }
                });
                channelRegistry.registerChannel(this);
            });
        }
    }

    @Override
    public ChannelConfig.ChannelEnum channel() {
        return ChannelConfig.ChannelEnum.FEI_SHU;
    }

    /**
     * 飞书消息扩展类（增加卡片ID、机器人ID）
     */
    @Data
    @Accessors(chain = true)
    @NoArgsConstructor
    public static class ChatbotMessageEx {
        private String robotId;
        private String aiCardId;
        private String openId;
        private String messageId;

        private String content;

        /**
         * p2p：单聊
         * group： 群组
         */
        private String chatType;

        /**
         * 消息类型
         */
        private String msgType;
    }

    /**
     * 注册飞书消息监听回调
     */
    private void registerMsgListenerCallback(String ownUserId, ChannelConfig config) {
        try {
            FeiShuBotProperties.FeiShuBotAccount account = (FeiShuBotProperties.FeiShuBotAccount) config;
            if (StringUtils.isBlank(account.getOwnerJobClawUserId())) {
                account.setOwnerJobClawUserId(ownUserId);
            }

            EventDispatcher eventDispatcher = EventDispatcher.newBuilder("", "")
                    .onP1MessageReadV1(new ImService.P1MessageReadV1Handler() {
                        @Override
                        public void handle(P1MessageReadV1 p1MessageReadV1) throws Exception {

                        }
                    })
                    .onP2MessageReceiveV1(new ImService.P2MessageReceiveV1Handler() {
                        @Override
                        public void handle(P2MessageReceiveV1 event) throws Exception {
                            // 接收对象说明： https://open.feishu.cn/document/server-docs/im-v1/message/events/receive
                            log.info("[ onP2MessageReceiveV1 access ], data: {}", Jsons.DEFAULT.toJson(event.getEvent()));

                            var eventData = event.getEvent();
                            // 1. 获取发送者 open_id（必须，用来指定发给谁）
                            String openId = eventData.getSender().getSenderId().getOpenId();

                            // 2. 获取消息 ID（可选，用于“回复消息”，不填就是直接发新消息）
                            String messageId = eventData.getMessage().getMessageId();

                            // 3. 创建流式卡片
                            var cardManager = cardManagers.get(config.getAppId());
                            String cardId = aiCardStatus.getActiveAiCard(account.getAppId(), openId);
                            if (cardId == null) {
                                cardId = cardManager.initStreamAiCardId(openId);
                                aiCardStatus.startAiCard(account.getAppId(), openId, cardId);
                            }

                            // 4. 上报消息
                            var content = eventData.getMessage().getContent();
                            var ex = new ChatbotMessageEx()
                                    .setRobotId(account.getAppId())
                                    .setAiCardId(cardId)
                                    .setOpenId(openId)
                                    .setMessageId(messageId)
                                    .setMsgType(eventData.getMessage().getMessageType())
                                    .setChatType(eventData.getMessage().getChatType())
                                    .setContent(content);
                            processMessage(MsgWrapper.<ChatbotMessageEx>builder().jobClawUserId(ownUserId).msg(ex).build());
                        }
                    })
                    .build();

            // 初始化飞书客户端
            Client feishuClient = new Client.Builder(config.getAppId(), config.getAppSecret())
                    .eventHandler(eventDispatcher)
                    .build();
            this.cardManagers.put(config.getAppId(), new StreamCardManager((com.git.hui.jobclaw.channels.FeiShuBotProperties.FeiShuBotAccount) config));
            feishuClient.start();

            // 刷新心跳配置
            this.channelRegistry.refreshChannelHeartBeatInfoIgnoreNull(ownUserId, name(), buildHeartBeatCallback(ownUserId));
            log.info("[FeiShu] Feishu bot channel started for user: {} - {}", ownUserId, account.getAppId());
        } catch (Exception e) {
            log.error("[FeiShu] Failed to start Feishu bot channel for user: {}", ownUserId, e);
            throw new RuntimeException("Failed to initialize Feishu channel", e);
        }
    }

    @Override
    public ChannelReceiveMessage adaptToReceive(MsgWrapper<ChatbotMessageEx> msgWrapper) {
        if (msgWrapper == null) {
            return null;
        }

        String robotOwnerUserId = msgWrapper.getJobClawUserId();
        ChatbotMessageEx msg = msgWrapper.getMsg();


        String jobClawUserId = null;
        ChannelConfig channelConfig = this.cardManagers.get(msgWrapper.getMsg().getRobotId()).account;
        String feishuId = msgWrapper.getMsg().getOpenId();
        var user = SpringUtil.getBean(IUserService.class).getUser(feishuId, channel());
        if (channelConfig.getScope() == ChannelConfig.ChannelScope.OWNER) {
            // 仅作者才能使用
            if (user == null || !String.valueOf(user.userId()).equals(robotOwnerUserId)) {
                errorResponse(msg, "这个机器人只为创作者本人服务哦~，若您是机器人的拥有者，请到个人中心->渠道配置->飞书机器人->绑定飞书账号: " + feishuId);
                return null;
            }
            jobClawUserId = robotOwnerUserId;
        } else if (channelConfig.getScope() == ChannelConfig.ChannelScope.LOGIN) {
            if (user == null) {
                log.warn("[DingDing] Failed to find user for staffId: {}", feishuId);
                errorResponse(msg, "您的个人JobClaw还没有绑定飞书渠道哦，请到个人中心->渠道配置->飞书机器人->绑定飞书账号: " + feishuId);
                return null;
            }
            jobClawUserId = String.valueOf(user.userId());
        } else if (channelConfig.getScope() == ChannelConfig.ChannelScope.VIP) {
            if (user == null) {
                errorResponse(msg, "这个机器人属于VIP专享哦~，请先到个人中心->渠道配置->飞书机器人->绑定飞书账号: " + feishuId);
                return null;
            } else if (user.role() != UserRoleEnum.VIP && user.role() != UserRoleEnum.ADMIN) {
                errorResponse(msg, "这个机器人属于VIP专享哦~，您可以到JobClaw开通VIP既可畅享对话");
                return null;
            }
            jobClawUserId = String.valueOf(user.userId());
        } else if (channelConfig.getScope() == ChannelConfig.ChannelScope.PUBLIC) {
            if (user == null) {
                // 公开的所有人都可以访问的场景，对于没有绑定的用户，直接使用钉钉的用户体系
                jobClawUserId = "F-" + feishuId;
            } else {
                jobClawUserId = String.valueOf(user.userId());
            }
        }
        msgWrapper.setJobClawUserId(jobClawUserId);


        JsonElement node = Jsons.DEFAULT.toJsonTree(Jsons.DEFAULT.fromJson(msg.getContent(), Map.class));
        String content = "";
        ChannelReceiveMessage.MediaMsg mediaMsg = null;
        ChannelReceiveMessage.FileMsg fileMsg = null;
        if ("text".equals(msg.getMsgType())) {
            // 文本消息
            content = node.getAsJsonObject().get("text").getAsString();
        } else if ("post".equals(msg.getMsgType())) {
            // 富文本
            // fixme 对于富文本，这里先只实现提取文本内容，忽略图片、文件
            JsonArray ary = node.getAsJsonObject().get("content").getAsJsonArray();
            StringBuilder contents = new StringBuilder();
            for (var tmp : ary) {
                for (var t : tmp.getAsJsonArray()) {
                    JsonObject obj = t.getAsJsonObject();
                    if (obj.get("tag").getAsString().equals("text")) {
                        contents.append(obj.get("text").getAsString()).append("\n");
                    }
                }
            }
            content = contents.toString();
        } else if ("image".equals(msg.getMsgType())) {
            // 图片
            var imgKey = node.getAsJsonObject().get("image_key").getAsString();
            var bytes = cardManagers.get(msg.getRobotId()).downloadResource(msg.getMessageId(), imgKey, "image");
            var tmpFile = channelStorageHelper.autoSaveFile(msgWrapper.getJobClawUserId(), name(), bytes, "png");
            mediaMsg = ChannelReceiveMessage.MediaMsg.builder().filePath(Path.of(tmpFile)).mimeType("image/png").build();
        } else if ("file".equals(msg.getMsgType()) || "sticker".equals(msg.getMsgType())) {
            // 文件 or 表情包
            var fileKey = node.getAsJsonObject().get("file_key").getAsString();
            var fileName = node.getAsJsonObject().get("file_name").getAsString();
            var fileType = fileName.contains(".") ? fileName.substring(fileName.lastIndexOf(".") + 1) : "txt";
            var bytes = cardManagers.get(msg.getRobotId()).downloadResource(msg.getMessageId(), fileKey, msg.getMsgType());
            var tmpFile = channelStorageHelper.autoSaveFile(msgWrapper.getJobClawUserId(), name(), bytes, fileType);
            fileMsg = ChannelReceiveMessage.FileMsg.builder().filePath(Path.of(tmpFile)).fileName(fileName).mimeType(MimeUtils.mimeByExt(fileType)).build();
        } else if ("audio".equals(msg.getMsgType())) {
            // 音频
            responseToUser(ChannelResponseMessage.builder()
                    .passThrough(Map.of("input", msg))
                    .toUserId(msg.getOpenId())
                    .jobClawUserId(msgWrapper.getJobClawUserId())
                    .type(ChannelResponseMessage.ResponseMessageType.TEXT)
                    .streamContents(Flux.just(new LlmRspCell(null, "暂不支持语音消息的处理哦~", null)))
                    .build());
        } else if ("video".equals(msg.getMsgType())) {
            // 视频
            responseToUser(ChannelResponseMessage.builder()
                    .passThrough(Map.of("input", msg))
                    .toUserId(msg.getOpenId())
                    .jobClawUserId(msgWrapper.getJobClawUserId())
                    .type(ChannelResponseMessage.ResponseMessageType.TEXT)
                    .streamContents(Flux.just(new LlmRspCell(null, "暂不支持视频消息的处理哦~", null)))
                    .build());
            return null;
        } else {
            // 表示不支持的消息类型
            responseToUser(ChannelResponseMessage.builder()
                    .passThrough(Map.of("input", msg))
                    .toUserId(msg.getOpenId())
                    .jobClawUserId(msgWrapper.getJobClawUserId())
                    .type(ChannelResponseMessage.ResponseMessageType.TEXT)
                    .streamContents(Flux.just(new LlmRspCell(null, "现在JobClaw还不支持处理这种类型的消息哦~", null)))
                    .build());
            return null;
        }


        return ChannelReceiveMessage.builder()
                .msgId(msg.getMessageId())
                .message(content)
                .fromUserId(msg.getOpenId())
                .jobClawUserId(msgWrapper.getJobClawUserId())
                .files(fileMsg != null ? List.of(fileMsg) : null)
                .medias(mediaMsg != null ? List.of(mediaMsg) : null)
                .channel(name())
                .groupTalk("group".equals(msg.getChatType()))
                .stream(true)
                .passThrough(Map.of("input", msg))
                .build();
    }

    private void errorResponse(ChatbotMessageEx msg, String content) {
        responseToUser(ChannelResponseMessage.builder()
                .passThrough(Map.of("input", msg))
                .toUserId(msg.getOpenId())
                .jobClawUserId(null)
                .type(ChannelResponseMessage.ResponseMessageType.TEXT)
                .streamContents(Flux.just(new LlmRspCell(null, content, null)))
                .build());
    }

    @Override
    public boolean saveHeartBeatConfig(MsgWrapper<ChatbotMessageEx> wrapper, boolean force) {
        var msg = wrapper.getMsg();
        String type = "group".equals(msg.getChatType()) ? "group" : "im";
        var prefix = buildHeartBeatKey(wrapper.getJobClawUserId(), msg.getRobotId(), type);
        // 如果存在配置，则不进行更新
        if (!force && configurationManager.getProperty(prefix) != null) {
            return false;
        }

        // 保存配置
        var response = ChannelResponseMessage.builder()
                .jobClawUserId(wrapper.getJobClawUserId())
                .toUserId(msg.getOpenId())
                .type(ChannelResponseMessage.ResponseMessageType.TEXT)
                .passThrough(Map.of("input", msg))
                .build();
        String value = JsonUtil.toStr(response);
        configurationManager.updateProperties(Map.of(prefix, value));
        return true;
    }

    @Override
    public Function<Object, ChannelResponseMessage> buildHeartBeatCallback(String jobClawUserId) {
        // fixme 群聊的主动推送消息的场景需要考虑如何做支持
        for (String robotId : cardManagers.keySet()) {
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

    /**
     * 响应消息给飞书用户（核心：支持流式/非流式响应）
     */
    @Override
    public boolean responseToUser(ChannelResponseMessage msg) {
        if (msg == null || msg.getToUserId() == null) {
            log.warn("[FeiShu] Invalid message or missing toUserId");
            return false;
        }

        ChatbotMessageEx originalMsg = (ChatbotMessageEx) msg.getPassThrough().get("input");
        // 流式返回的场景
        var cardManager = cardManagers.get(originalMsg.getRobotId());
        var stream = msg.getStreamContents();
        String feiShuOpenId = originalMsg.getOpenId();
        String cardId = originalMsg.getAiCardId();
        if (stream != null) {
            if (StringUtils.isBlank(cardId)) {
                // 通常是后台主动给用户发送消息的场景
                cardId = aiCardStatus.getActiveAiCard(originalMsg.getRobotId(), feiShuOpenId);
                if (cardId == null) {
                    cardId = cardManager.initStreamAiCardId(originalMsg.getOpenId());
                }
            }

            if (StringUtils.isBlank(cardId)) {
                var content = stream.blockLast();
                return cardManager.directReply(originalMsg.getOpenId(), content.content(), ResponseType.TEXT);
            } else {
                StringBuilder thinking = new StringBuilder();
                StringBuilder content = new StringBuilder();
                StringBuilder toolInfo = new StringBuilder();
                StringBuilder toolResultInfo = new StringBuilder();
                String finalCardId = cardId;
                stream.doOnNext(response -> {
                            if (log.isDebugEnabled()) {
                                log.debug("[FeiShu] Received response chunk: {}", response);
                            }
                            if (StringUtils.isNotBlank(response.thinking())) {
                                thinking.append(response.thinking());
                            }
                            if (StringUtils.isNotBlank(response.tool())) {
                                toolInfo.append(response.tool()).append("\n");
                                log.info("[FeiShu] Tool call detected: {}", response.tool());
                            }
                            if (StringUtils.isNotBlank(response.toolResult())) {
                                toolResultInfo.append(response.toolResult()).append("\n");
                                log.info("[FeiShu] Tool result received, length={}", response.toolResult().length());
                            }
                            if (!StringUtils.isEmpty(response.content())) {
                                if (content.isEmpty()) {
                                    // 表示首次响应正文内容，此时为了更好的用户体验，我们可以调用飞书接口，将面板关闭
                                    cardManager.autoCloseThinkingHeader(finalCardId);
                                }

                                content.append(response.content());
                            }
                            // 更新工具调用信息
                            if (toolInfo.length() > 0) {
                                cardManager.updateStreamingCard(finalCardId, toolInfo.toString(), StreamCardUpdateContentType.TOOL_REQ);
                            }
                            // 更新工具执行结果
                            if (toolResultInfo.length() > 0) {
                                cardManager.updateStreamingCard(finalCardId, toolResultInfo.toString(), StreamCardUpdateContentType.TOOL_RSP);
                            }
                            cardManager.updateStreamingCard(finalCardId, thinking.toString(), content.toString(), false);
                            aiCardStatus.answerAiCard(originalMsg.robotId, feiShuOpenId, finalCardId);
                        })
                        .doOnError(error -> {
                            log.error("[FeiShu] Error in stream response for cardId: {}", finalCardId, error);
                            // 发生错误时，标记卡片为结束状态
                            cardManager.updateStreamingCard(finalCardId, thinking.toString(), "抱歉，生成回复时遇到了错误。", true);
                            aiCardStatus.finishAiCard(originalMsg.robotId, feiShuOpenId, finalCardId);
                        })
                        .doOnComplete(() -> {
                            log.info("[FeiShu] Stream response completed for cardId: {}, total length: {}", finalCardId, content.length());
                            // 流式响应完成，标记卡片为结束状态
                            cardManager.updateStreamingCard(finalCardId, thinking.toString(), content.toString(), true);
                            aiCardStatus.finishAiCard(originalMsg.robotId, feiShuOpenId, finalCardId);
                        }).subscribe();
            }
        } else {
            // 非流式返回的场景，直接回复
            String content = msg.getContent();
            if (StringUtils.isBlank(content)) {
                content = "出现故障了~现在暂无返回，请稍后再试";
            }

            if (StringUtils.isBlank(cardId)) {
                cardId = aiCardStatus.getActiveAiCard(originalMsg.getRobotId(), msg.getJobClawUserId());
                if (cardId != null) {
                    cardManager.updateStreamingCard(cardId, "", content, true);
                    aiCardStatus.finishAiCard(originalMsg.getRobotId(), feiShuOpenId, cardId);
                    return true;
                }

                // 如果会话链接有效，那就通过这个回调发送消息即可
                if (cardManager.directReply(originalMsg.getOpenId(), content, ResponseType.TEXT)) {
                    return true;
                }

                // 后台主动给用户推送消息的场景，主动创建一个AiCard，用于推送消息
                cardId = cardManager.initStreamAiCardId(originalMsg.getOpenId());
                if (cardId == null) {
                    return false;
                }
            }
            cardManager.updateStreamingCard(cardId, "", content, true);
            // 主动结束这个流式卡片，避免被再次更新
            aiCardStatus.finishAiCard(originalMsg.getRobotId(), feiShuOpenId, cardId);
        }
        return true;
    }


    @Override
    public <T extends ChannelConfig> void addAccount(T channelConfig) {
        if (channelConfig instanceof FeiShuBotProperties.FeiShuBotAccount) {
            FeiShuBotProperties.FeiShuBotAccount account = (FeiShuBotProperties.FeiShuBotAccount) channelConfig;
            registerMsgListenerCallback(account.getOwnerJobClawUserId(), account);
            channelRegistry.registerChannel(this);
        } else {
            log.warn("[FeiShu] Unsupported config type: {}", channelConfig.getClass().getName());
        }
    }


    private enum StreamCardUpdateContentType {
        // 思考过程
        THINKING("thinking_1"),
        // 正文
        CONTENT("content_1"),
        // Tool to execute
        TOOL_REQ("tool_req"),
        // tool response
        TOOL_RSP("tool_rsp");
        private String key;

        StreamCardUpdateContentType(String key) {
            this.key = key;
        }
    }

    private enum ResponseType {
        TEXT("text") {
            @Override
            public String buildContent(String content) {
                return "{\"text\":\"" + content + "\"}";
            }
        },
        CARD("interactive") {
            @Override
            public String buildContent(String content) {
                return "{\"type\":\"card\",\"data\":{\"card_id\":\"" + content + "\"}}";
            }
        },
        ;

        private String type;

        ResponseType(String type) {
            this.type = type;
        }

        public abstract String buildContent(String content);
    }

    public static class StreamCardManager {

        private com.lark.oapi.Client client;
        // 每个卡片的计数序号，用于更新卡片时传入这个序号，要求单调递增
        private Map<String, Integer> aiCardSeq = new ConcurrentHashMap<>();

        private com.git.hui.jobclaw.channels.FeiShuBotProperties.FeiShuBotAccount account;

        public StreamCardManager(FeiShuBotProperties.FeiShuBotAccount account) {
            this.account = account;
            this.client = com.lark.oapi.Client.newBuilder(account.getAppId(), account.getAppSecret()).build();
        }

        private String initStreamAiCardId(String receiveId) {
            String cardId = createStreamingCard();
            if (cardId != null) {
                if (directReply(receiveId, cardId, ResponseType.CARD)) {
                    aiCardSeq.put(cardId, 0);
                    return cardId;
                }
            }
            return null;
        }

        private boolean directReply(String receiveId, String content, ResponseType type) {
            // 构造消息内容
            CreateMessageReq req = CreateMessageReq.newBuilder()
                    .receiveIdType("open_id") // 固定使用 open_id
                    .createMessageReqBody(CreateMessageReqBody.newBuilder()
                            .receiveId(receiveId) // 接收人
                            .uuid(UUID.randomUUID().toString())
                            .msgType(type.type)    // 消息类型：文本
                            .content(type.buildContent(content))
                            .build())
                    .build();

            // 调用接口发送消息
            try {
                CreateMessageResp resp = client.im().message().create(req);
                if (log.isDebugEnabled()) {
                    log.debug("[FeiShu] Direct reply success: {}", resp.getData().getMessageId());
                }
                return resp.success();
            } catch (Exception e) {
                log.error("[FeiShu] Direct reply error", e);
                return false;
            }
        }


        private String createStreamingCard() {
            CreateCardReq req = CreateCardReq.newBuilder()
                    .createCardReqBody(
                            CreateCardReqBody
                                    .newBuilder()
                                    .type("card_json")
                                    .data("""
                                            {
                                                "schema": "2.0",
                                                "header": {
                                                },
                                                "config": {
                                                    "streaming_mode": true,
                                                    "summary": {
                                                        "content": ""
                                                    },
                                                    "streaming_config": {
                                                        "print_frequency_ms": {
                                                            "default": 70,
                                                            "android": 70,
                                                            "ios": 70,
                                                            "pc": 70
                                                        },
                                                        "print_step": {
                                                            "default": 1,
                                                            "android": 1,
                                                            "ios": 1,
                                                            "pc": 1
                                                        },
                                                        "print_strategy": "fast"
                                                    }
                                                },
                                                "body": {
                                                    "elements": [
                                                        {
                                                          "tag": "collapsible_panel",
                                                          "expanded": true,
                                                          "header": {
                                                            "title": {
                                                              "tag": "plain_text",
                                                              "content": "🤔 推理（可折叠）",
                                                              "element_id": "thinking_title"
                                                            }
                                                          },
                                                          "elements": [
                                                            {
                                                                "tag": "markdown",
                                                                "content": "",
                                                                "element_id": "thinking_1"
                                                            },
                                                            {
                                                                "tag": "markdown",
                                                                "content": "",
                                                                "element_id": "tool_req"
                                                            },
                                                            {
                                                                "tag": "markdown",
                                                                "content": "",
                                                                "element_id": "tool_rsp"
                                                            }
                                                          ]
                                                        },
                                                        {
                                                              "tag": "hr"
                                                        },
                                                        {
                                                            "tag": "markdown",
                                                            "content": "",
                                                            "element_id": "content_1"
                                                        }
                                                    ]
                                                }
                                            }
                                            """)
                                    .build())
                    .build();

            try {

                CreateCardResp resp = client.cardkit().v1().card().create(req);
                // 从返回里拿 card_id
                return resp.getData().getCardId();
            } catch (Exception e) {
                log.error("[FeiShu] Create streaming card error", e);
                return null;
            }
        }

        private void updateStreamingCard(String cardId, String thinking, String content, boolean finish) {
            if (StringUtils.isBlank(content)) {
                if (StringUtils.isBlank(thinking)) {
                    return;
                }
                thinking = "> " + thinking.trim().replaceAll("\n", "\n> ");
                updateStreamingCard(cardId, thinking, StreamCardUpdateContentType.THINKING);
            } else {
                updateStreamingCard(cardId, content, StreamCardUpdateContentType.CONTENT);
            }
            if (finish) {
                completeCard(cardId);
            }
        }

        private void updateStreamingCard(String cardId, String content, StreamCardUpdateContentType type) {
            try {
                if (StringUtils.isEmpty(content)) {
                    return;
                }

                // 流式更新卡片内容
                int seq = incrSeq(cardId);
                ContentCardElementReq req = ContentCardElementReq.newBuilder()
                        .cardId(cardId)
                        .elementId(type.key)
                        .contentCardElementReqBody(ContentCardElementReqBody.newBuilder()
                                .uuid(UUID.randomUUID().toString())
                                .content(content)
                                .sequence(seq)
                                .build())
                        .build();

                ContentCardElementResp resp = client.cardkit().v1().cardElement().content(req);
                if (log.isDebugEnabled()) {
                    log.debug("[FeiShu] Update streaming card success: {}", Jsons.DEFAULT.toJson(resp));
                }
            } catch (Exception e) {
                log.error("[FeiShu] Update streaming card error", e);
            }
        }

        private void autoCloseThinkingHeader(String cardId) {
            // todo 待实现
            return;
        }

        private void completeCard(String cardId) {
            // 设置卡片更新完成
            // 流式更新卡片内容
            int seq = incrSeq(cardId);
            SettingsCardReq req = SettingsCardReq.newBuilder()
                    .cardId(cardId)
                    .settingsCardReqBody(SettingsCardReqBody.newBuilder()
                            .settings("""
                                    {
                                    "config": {
                                            "streaming_mode": false,
                                            "summary": {
                                                "content": ""
                                            },
                                            "streaming_config": {
                                                "print_frequency_ms": {
                                                    "default": 70,
                                                    "android": 70,
                                                    "ios": 70,
                                                    "pc": 70
                                                },
                                                "print_step": {
                                                    "default": 1,
                                                    "android": 1,
                                                    "ios": 1,
                                                    "pc": 1
                                                },
                                                "print_strategy": "fast"
                                            }
                                        }
                                    }
                                    """)
                            .uuid("a0d69e20-1dd1-458b-k525-dfeca4015204")
                            .sequence(seq)
                            .build())
                    .build();

            // 发起请求
            try {
                SettingsCardResp resp = client.cardkit().v1().card().settings(req);
                if (log.isDebugEnabled()) {
                    log.debug("[FeiShu] Complete streaming card success: {}", Jsons.DEFAULT.toJson(resp));
                }
            } catch (Exception e) {
                log.error("[FeiShu] Complete streaming card error", e);
            } finally {
                removeSeq(cardId);
            }
        }

        private int incrSeq(String cardId) {
            // 流式更新卡片内容
            int seq = aiCardSeq.getOrDefault(cardId, 0) + 1;
            aiCardSeq.put(cardId, seq);
            return seq;
        }

        private void removeSeq(String cardId) {
            aiCardSeq.remove(cardId);
        }


        /**
         * 资源文件下载
         * @param msgId
         * @param fileKey
         * @param type
         */
        public byte[] downloadResource(String msgId, String fileKey, String type) {
            // 创建请求对象
            GetMessageResourceReq req = GetMessageResourceReq.newBuilder()
                    .messageId(msgId)
                    .fileKey(fileKey)
                    .type(type)
                    .build();
            // 发起请求
            try {
                GetMessageResourceResp resp = client.im().v1().messageResource().get(req);
                return resp.getData().toByteArray();
            } catch (Exception e) {
                log.error("[FeiShu] Download resource error: {}.{}", fileKey, type, e);
                return null;
            }
        }
    }
}