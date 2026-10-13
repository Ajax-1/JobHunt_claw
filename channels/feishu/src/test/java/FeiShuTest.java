import com.lark.oapi.core.request.EventReq;
import com.lark.oapi.core.utils.Jsons;
import com.lark.oapi.event.CustomEventHandler;
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
import com.lark.oapi.service.im.v1.model.P1MessageReadV1;
import com.lark.oapi.service.im.v1.model.P2MessageReceiveV1;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * https://open.feishu.cn/document/cardkit-v1/streaming-updates-openapi-overview#461aa643
 * @date 2026/4/16
 */
public class FeiShuTest {

    private static final String appId = "xx";
    private static final String appSecret = "xx";

    public static void main(String[] args) throws InterruptedException {
        EventDispatcher EVENT_HANDLER = EventDispatcher.newBuilder("", "")
                .onP1MessageReadV1(new ImService.P1MessageReadV1Handler() {
                    @Override
                    public void handle(P1MessageReadV1 p1MessageReadV1) throws Exception {
                        System.out.println("消息已读事件: " + Jsons.DEFAULT.toJson(p1MessageReadV1));
                    }
                })
                .onP2MessageReceiveV1(new ImService.P2MessageReceiveV1Handler() {
                    @Override
                    public void handle(P2MessageReceiveV1 event) throws Exception {
                        System.out.printf("[ onP2MessageReceiveV1 access ], data: %s\n", Jsons.DEFAULT.toJson(event.getEvent()));

                        var eventData = event.getEvent();
                        // 1. 获取发送者 open_id（必须，用来指定发给谁）
                        String openId = eventData.getSender().getSenderId().getOpenId();
                        System.out.printf("[ onP2MessageReceiveV1 access ], openId: %s\n", openId);

                        // 2. 获取消息 ID（可选，用于“回复消息”，不填就是直接发新消息）
                        String messageId = eventData.getMessage().getMessageId();

                        streamRsp(openId);
                    }
                })
                .onCustomizedEvent("这里填入你要自定义订阅的 event 的 key，例如 out_approval", new CustomEventHandler() {
                    @Override
                    public void handle(EventReq event) throws Exception {
                        System.out.printf("[ onCustomizedEvent access ], type: message, data: %s\n", new String(event.getBody(), StandardCharsets.UTF_8));
                    }
                })
                .build();

        // 初始化飞书客户端
        com.lark.oapi.ws.Client feishuClient = new com.lark.oapi.ws.Client.Builder(appId, appSecret)
                .eventHandler(EVENT_HANDLER)
                .build();
        feishuClient.start();

        // 直接发送，用于测试流式更新的场景
        streamRsp("ou_1ed51a89affb819aa040137d59657800");
        while (true) {
            Thread.sleep(50_000);
        }
    }


    private static String streamRsp(String receiveId) {
        var client = com.lark.oapi.Client.newBuilder(appId, appSecret).build();
        try {
            // 创建请求对象
            CreateCardReq req = CreateCardReq.newBuilder()
                    .createCardReqBody(
                            CreateCardReqBody
                                    .newBuilder()
                                    .type("card_json")
                                    .data("""
                                            {
                                                "schema": "2.0",
                                                "header": {
                                                    "tag":"plain_text",
                                                    "content": "卡片标题"
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
                                                              "content": "🤔 推理思考（可折叠）"
                                                            }
                                                          },
                                                          "elements": [
                                                            {
                                                                "tag": "markdown",
                                                                "content": "推理内容",
                                                                "element_id": "thinking_1"
                                                            }
                                                          ]
                                                        },
                                                        {
                                                              "tag": "hr"
                                                        },
                                                        {
                                                            "tag": "markdown",
                                                            "content": "正文内容",
                                                            "element_id": "markdown_1"
                                                        }
                                                    ]
                                                }
                                            }
                                            """)
                                    .build())
                    .build();


            CreateCardResp resp = client.cardkit().v1().card().create(req);
            // 从返回里拿 card_id
            String cardId = resp.getData().getCardId();
            System.out.println("创建卡片成功：" + cardId);

            // 发送消息卡片
            // 3. 构造消息内容
            CreateMessageReq msgReq = CreateMessageReq.newBuilder()
                    .receiveIdType("open_id") // 固定使用 open_id
                    .createMessageReqBody(CreateMessageReqBody.newBuilder()
                            .receiveId(receiveId) // 接收人
                            .uuid(UUID.randomUUID().toString())
                            .msgType("interactive")    // 消息类型：文本
                            .content("{\"type\":\"card\",\"data\":{\"card_id\":\"" + cardId + "\"}}")
                            .build())
                    .build();
            CreateMessageResp msgRsp = client.im().v1().message().create(msgReq);
            System.out.println("发送卡片成功：" + cardId + "  msgId = " + msgRsp.getData());


            StringBuilder builder = new StringBuilder();
            System.out.println("发送卡片思考内容：" + cardId);
            AtomicInteger seq = new AtomicInteger(1);
            for (int i = 1; i < 10; i++) {
                builder.append("*");
                String tk = ((10 * i) + "%\n" + builder.toString());
                String thinking = "> " + tk.trim().replaceAll("\n", "\n> ");
                updateStreamingCard(client, cardId, seq.addAndGet(1), thinking, "thinking_1");
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            System.out.println("发送卡片正文内容：" + cardId);
            for (int i = 11; i < 20; i++) {
                builder.append("*");
                updateStreamingCard(client, cardId, seq.addAndGet(1), (10 * i) + "%" + builder.toString(), "markdown_1");
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            // 结束卡片
            completeCard(client, cardId, seq.addAndGet(1));
            return cardId;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private static void updateStreamingCard(com.lark.oapi.Client client, String cardId, int seq, String content, String elementId) {
        try {
            // 流式更新卡片内容
            ContentCardElementReq req = ContentCardElementReq.newBuilder()
                    .cardId(cardId)
                    .elementId(elementId)
                    .contentCardElementReqBody(ContentCardElementReqBody.newBuilder()
                            .uuid(UUID.randomUUID().toString())
                            .content(content)
                            .sequence(seq)
                            .build())
                    .build();

            ContentCardElementResp resp = client.cardkit().v1().cardElement().content(req);
            System.out.println("更新卡片成功：" + Jsons.DEFAULT.toJson(resp));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void completeCard(com.lark.oapi.Client client, String cardId, int seq) {
        // 设置卡片更新完成
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
            System.out.println("设置卡片更新完成：" + Jsons.DEFAULT.toJson(resp));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
