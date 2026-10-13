package com.git.hui.jobclaw.channels.sdk;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.aliyun.dingtalkcard_1_0.Client;
import com.aliyun.dingtalkcard_1_0.models.CreateAndDeliverHeaders;
import com.aliyun.dingtalkcard_1_0.models.CreateAndDeliverRequest;
import com.aliyun.dingtalkcard_1_0.models.CreateAndDeliverResponse;
import com.aliyun.dingtalkcard_1_0.models.StreamingUpdateHeaders;
import com.aliyun.dingtalkcard_1_0.models.StreamingUpdateRequest;
import com.aliyun.tea.TeaException;
import com.aliyun.teaopenapi.models.Config;
import com.aliyun.teautil.models.RuntimeOptions;
import com.dingtalk.api.DefaultDingTalkClient;
import com.dingtalk.api.DingTalkClient;
import com.dingtalk.api.request.OapiGettokenRequest;
import com.dingtalk.api.response.OapiGettokenResponse;
import com.dingtalk.open.app.api.OpenDingTalkStreamClientBuilder;
import com.dingtalk.open.app.api.callback.DingTalkStreamTopics;
import com.dingtalk.open.app.api.callback.OpenDingTalkCallbackListener;
import com.dingtalk.open.app.api.models.bot.ChatbotMessage;
import com.dingtalk.open.app.api.security.AuthClientCredential;
import com.git.hui.jobclaw.channels.DingDingBotProperties;
import com.git.hui.jobclaw.core.channel.ChannelReceiveMessage;
import com.git.hui.jobclaw.core.utils.MimeUtils;
import com.git.hui.jobclaw.core.utils.json.JsonUtil;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

/**
 *
 * @date 2026/4/22
 */
@Slf4j
public class DingDingSdk {
    @Getter
    private DingDingBotProperties.DingDingBotAccount dingDingBotAccount;


    private Client client;

    private com.aliyun.dingtalkrobot_1_0.Client robotClient;
    private String accessToken;
    private long expireTime;

    public static String genAiCardTrackId() {
        return "ai_card_" + System.currentTimeMillis();
    }

    public DingDingSdk(DingDingBotProperties.DingDingBotAccount dingDingBotAccount) {
        this.dingDingBotAccount = dingDingBotAccount;
        // 使用虚拟线程进行异步初始化
        Thread.ofVirtual().start(() -> {
            initCorpToken();
            initRobotClient();
            initClient();
        });
    }


    public void start(Consumer<ChatbotMessage> runnable) throws Exception {
        var dingTalkClient = OpenDingTalkStreamClientBuilder.custom()
                .credential(new AuthClientCredential(dingDingBotAccount.getAppId(), dingDingBotAccount.getAppSecret()))
                .registerCallbackListener(DingTalkStreamTopics.BOT_MESSAGE_TOPIC,
                        (OpenDingTalkCallbackListener<ChatbotMessage, Void>) chatbotMessage -> {
                            // 接收到消息之后，发送到消息总线 bus，然后通过sink方式接收大模型的返回，最后将结果响应给用户
                            log.info("[DingDing] Received message from DingDing msg={}", JsonUtil.toStr(chatbotMessage));
                            runnable.accept(chatbotMessage);
                            return null;
                        })
                .build();
        dingTalkClient.start();
    }


    private void initClient() {
        try {
            Config config = new Config();
            config.protocol = "https";
            config.regionId = "central";
            this.client = new com.aliyun.dingtalkcard_1_0.Client(config);
        } catch (Exception e) {
            log.error("[DingDing] createClient get exception, msg:{}", e.getMessage());
        }
    }

    private void initRobotClient() {
        try {
            com.aliyun.teaopenapi.models.Config config = new com.aliyun.teaopenapi.models.Config();
            config.protocol = "https";
            config.regionId = "central";
            this.robotClient = new com.aliyun.dingtalkrobot_1_0.Client(config);
        } catch (Exception e) {
            log.error("[DingDing] createClient get exception, msg:{}", e.getMessage());
        }
    }

    private void initCorpToken() {
        try {
            DingTalkClient client = new DefaultDingTalkClient("https://oapi.dingtalk.com/gettoken");
            OapiGettokenRequest request = new OapiGettokenRequest();
            request.setAppkey(this.dingDingBotAccount.getAppId());
            request.setAppsecret(this.dingDingBotAccount.getAppSecret());
            request.setHttpMethod("GET");
            OapiGettokenResponse response = client.execute(request);
            log.info("[DingDing] getCorpToken, resp:{}", response.getBody());
            JSONObject obj = JSON.parseObject(response.getBody());
            this.accessToken = obj.getString("access_token");
            this.expireTime = System.currentTimeMillis() + obj.getLongValue("expires_in") * 1000 - 60_000;
        } catch (Exception e) {
            log.error("[DingDing] getCorpToken get exception, msg:{}", e.getMessage());
        }
    }

    private String getAccessToken() {
        if (System.currentTimeMillis() > expireTime) {
            initCorpToken();
        }
        return accessToken;
    }


    public DingDingMsgContent parseContent(ChatbotMessageEx msg) {
        var content = "";
        ChannelReceiveMessage.MediaMsg mediaMsg = null;
        ChannelReceiveMessage.FileMsg fileMsg = null;
        if (msg.getMsgtype().equals("text")) {
            content = msg.getText().getContent();
        } else if (msg.getMsgtype().equals("audio")) {
            // 语音，直接获取语音识别的内容
            content = msg.getContent().getRecognition();
        }
        // 发送的是图片消息、语音消息、视频消息、文件消息、富文本消息时
        else if (msg.getMsgtype().equals("video")) {
            var downloadCode = msg.getContent().getDownloadCode();
            var downUrl = downloadFile(downloadCode);
            mediaMsg = ChannelReceiveMessage.MediaMsg.builder()
                    .fileType("mp4")
                    .mimeType("video/mp4")
                    .downUrl(downUrl)
                    .build();
        } else if (msg.getMsgtype().equals("picture")) {
            var downloadCode = msg.getContent().getDownloadCode();
            var fileType = "png";
            var downUrl = downloadFile(downloadCode);
            mediaMsg = ChannelReceiveMessage.MediaMsg.builder()
                    .mimeType("image/png")
                    .fileType(fileType)
                    .downUrl(downUrl)
                    .build();
        } else if (msg.getMsgtype().equals("file")) {
            var downloadCode = msg.getContent().getDownloadCode();
            String fileName = msg.getContent().getFileName();
            var fileType = "txt";
            if (fileName != null && fileName.contains(".")) {
                fileType = fileName.substring(fileName.lastIndexOf(".") + 1);
            }
            var downUrl = downloadFile(downloadCode);
            fileMsg = ChannelReceiveMessage.FileMsg.builder()
                    .fileName(fileName)
                    .fileType(fileType)
                    .downUrl(downUrl)
                    .mimeType(MimeUtils.mimeByExt(fileType))
                    .build();
        } else if (msg.getMsgtype().equals("richText")) {
            // todo 富文本这里，先只处理文本
            StringBuilder contentBuilder = new StringBuilder();
            for (var item : msg.getContent().getRichText()) {
                contentBuilder.append(item.getContent());
            }
            content = contentBuilder.toString();
        } else {
            log.warn("[DingDing] Unsupported message type: {}", msg.getMsgtype());
            return null;
        }
        return new DingDingMsgContent(content, mediaMsg, fileMsg);
    }


    public String downloadFile(String downloadCode) {
        com.aliyun.dingtalkrobot_1_0.models.RobotMessageFileDownloadHeaders robotMessageFileDownloadHeaders = new com.aliyun.dingtalkrobot_1_0.models.RobotMessageFileDownloadHeaders();
        robotMessageFileDownloadHeaders.xAcsDingtalkAccessToken = getAccessToken();
        com.aliyun.dingtalkrobot_1_0.models.RobotMessageFileDownloadRequest robotMessageFileDownloadRequest = new com.aliyun.dingtalkrobot_1_0.models.RobotMessageFileDownloadRequest()
                .setDownloadCode(downloadCode)
                .setRobotCode(dingDingBotAccount.getAppId());
        try {
            var response = robotClient.robotMessageFileDownloadWithOptions(robotMessageFileDownloadRequest,
                    robotMessageFileDownloadHeaders,
                    new com.aliyun.teautil.models.RuntimeOptions());
            return response.getBody().getDownloadUrl();
        } catch (TeaException err) {
            log.error("[DingDing] getCorpToken get exception, msg:{}", err.getMessage(), err);
        } catch (Exception _err) {
            log.error("[DingDing] getCorpToken get exception, msg:{}", _err.getMessage(), _err);
        }
        return null;
    }


    public String initStreamAiCardId(String robotId, ChatbotMessage chatbotMessage) {
        if (StringUtils.isNotBlank(dingDingBotAccount.getAiCardId())) {
            String cardId = DingDingSdk.genAiCardTrackId();
            if (Objects.equals(chatbotMessage.getConversationType(), "1")) {
                createImCard(cardId, robotId, chatbotMessage.getSenderStaffId());
            } else {
                createGroupCard(cardId, robotId, chatbotMessage.getSenderStaffId(), chatbotMessage.getConversationId());
            }
            return cardId;
        }
        return null;
    }


    /**
     * 创建私聊的AI卡片
     * @param outTrackId
     * @param robotCode
     * @param userId
     */
    private void createImCard(String outTrackId, String robotCode, String userId) {
        try {
            CreateAndDeliverHeaders headers = new CreateAndDeliverHeaders();
            headers.xAcsDingtalkAccessToken = getAccessToken();

            CreateAndDeliverRequest.CreateAndDeliverRequestImRobotOpenDeliverModel imRobotOpenDeliverModel
                    = new CreateAndDeliverRequest.CreateAndDeliverRequestImRobotOpenDeliverModel().setSpaceType(
                    "IM_ROBOT").setRobotCode(robotCode);


            CreateAndDeliverRequest.CreateAndDeliverRequestImRobotOpenSpaceModel imRobotOpenSpaceModel
                    = new CreateAndDeliverRequest.CreateAndDeliverRequestImRobotOpenSpaceModel().setSupportForward(
                    true);

            Map<String, String> cardDataMap = new HashMap<>();
            cardDataMap.put("content", "# 正在思考中...");

            CreateAndDeliverRequest.CreateAndDeliverRequestCardData cardData = new CreateAndDeliverRequest.CreateAndDeliverRequestCardData()
                    .setCardParamMap(cardDataMap);


            CreateAndDeliverRequest request
                    = new CreateAndDeliverRequest()
                    .setOutTrackId(outTrackId)
                    .setUserId(userId)
                    .setCardTemplateId(this.dingDingBotAccount.getAiCardId())
                    .setCallbackType("STREAM")
                    .setCardData(cardData)
                    .setImRobotOpenSpaceModel(imRobotOpenSpaceModel)
                    .setImRobotOpenDeliverModel(imRobotOpenDeliverModel)
                    .setOpenSpaceId("dtv1.card//im_robot." + userId)
                    .setUserIdType(1);

            CreateAndDeliverResponse resp = client.createAndDeliverWithOptions(request, headers,
                    new RuntimeOptions());
            if (log.isDebugEnabled()) {
                log.debug("[DingDing] CardManager#initImCard get resp:{}", JSON.toJSONString(resp));
            }
        } catch (Exception e) {
            log.warn("[DingDing] CardManager#initImCard get exception, msg:{}", e.getMessage());
        }
    }

    private void createGroupCard(String outTrackId, String robotCode, String userId, String conversationId) {
        try {
            CreateAndDeliverHeaders headers = new CreateAndDeliverHeaders();
            headers.xAcsDingtalkAccessToken = getAccessToken();

            CreateAndDeliverRequest.CreateAndDeliverRequestImGroupOpenDeliverModel imGroupOpenDeliverModel = new CreateAndDeliverRequest.CreateAndDeliverRequestImGroupOpenDeliverModel()
                    .setRobotCode(robotCode)
                    // 卡片接收人
                    .setRecipients(List.of(userId));

            CreateAndDeliverRequest.CreateAndDeliverRequestImGroupOpenSpaceModel imGroupOpenSpaceModel = new CreateAndDeliverRequest.CreateAndDeliverRequestImGroupOpenSpaceModel()
                    .setSupportForward(true);

            Map<String, String> cardDataMap = new HashMap<>();
            cardDataMap.put("content", "# 正在思考中...");

            CreateAndDeliverRequest.CreateAndDeliverRequestCardData cardData = new CreateAndDeliverRequest.CreateAndDeliverRequestCardData()
                    .setCardParamMap(cardDataMap);
            CreateAndDeliverRequest createAndDeliverRequest = new CreateAndDeliverRequest()
                    .setUserId(userId)
                    .setCardTemplateId(this.dingDingBotAccount.getAiCardId())
                    // 用于标识卡片的唯一 ID，业务需自行建立关联关系，用于后续的卡片更新
                    .setOutTrackId(outTrackId)
                    .setCallbackType("STREAM")
                    .setCardData(cardData)
                    .setImGroupOpenSpaceModel(imGroupOpenSpaceModel)
                    .setImGroupOpenDeliverModel(imGroupOpenDeliverModel)
                    .setOpenSpaceId("dtv1.card//im_group." + conversationId)
                    .setUserIdType(1);
            var rsp = client.createAndDeliverWithOptions(createAndDeliverRequest, headers, new RuntimeOptions());
            if (log.isDebugEnabled()) {
                log.debug("[DingDing] CardManager#initGroupCard get resp:{}", JSON.toJSONString(rsp));
            }
        } catch (Exception e) {
            log.warn("[DingDing] CardManager#initGroupCard get exception, msg:{}", e.getMessage());
        }
    }

    public void streamUpdate(String outTrackId, String thinking, String content, boolean isFinalize) {
        try {
            String showValue;
            if (StringUtils.isNotBlank(content)) {
                showValue = content;
            } else {
                showValue = "Thinking: " + thinking;
            }

            StreamingUpdateHeaders headers = new StreamingUpdateHeaders();
            headers.xAcsDingtalkAccessToken = getAccessToken();
            StreamingUpdateRequest request =
                    new StreamingUpdateRequest()
                            .setOutTrackId(outTrackId)
                            .setGuid(UUID.randomUUID().toString())
                            .setKey("content")
                            .setContent(showValue)
                            .setIsFull(true)
                            .setIsFinalize(isFinalize);
            var res = client.streamingUpdateWithOptions(request, headers, new RuntimeOptions());
            if (log.isDebugEnabled()) {
                log.debug("[DingDing] CardManager#streamUpdate get res:{}", JSON.toJSONString(res));
            }

        } catch (Exception e) {
            log.error("[DingDing] CardManager#streamUpdate get exception, msg:{}", e.getMessage());
        }
    }
}
