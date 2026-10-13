package com.git.hui.jobclaw.gather.service;

import com.git.hui.jobclaw.core.agent.extraction.ExtractionReport;
import com.git.hui.jobclaw.core.agent.extraction.SourceRecord;
import com.git.hui.jobclaw.core.agent.extraction.StructuredBatchExtractor;
import org.springframework.beans.factory.annotation.Value;
import cn.hutool.core.util.CharsetUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.http.HttpUtil;
import com.git.hui.jobclaw.constants.gather.GatherModelTypeEnum;
import com.git.hui.jobclaw.gather.model.GatherOcDraftBo;
import com.git.hui.jobclaw.gather.model.ModelSelectReq;
import com.git.hui.jobclaw.gather.service.ai.OcAiModelContext;
import com.git.hui.jobclaw.gather.service.ai.OcChatModelApi;
import com.git.hui.jobclaw.agents.jobfetch.util.GatherResFormat;
import com.git.hui.jobclaw.core.utils.json.JsonUtil;
import io.modelcontextprotocol.client.McpAsyncClient;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.content.Media;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.mcp.AsyncMcpToolCallbackProvider;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.StaticToolCallbackProvider;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.MimeType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * AI采集代理
 *
 * @date 2025/7/14
 */
@Slf4j
@Component
public class GatherAiAgent {
    // 最大聊天次数
    private final Integer MAX_CHAT_CNT = 10;
    private final OcAiModelContext ocAiModelContext;
    private BeanOutputConverter<ArrayList<GatherOcDraftBo>> gatherResConverter;

    /**
     * 给大模型使用的工具提供类
     */
    private ToolCallbackProvider toolCallbackProvider;

    @Autowired
    public GatherAiAgent(OcAiModelContext aiModelFacade) {
        this.ocAiModelContext = aiModelFacade;
        this.gatherResConverter = new BeanOutputConverter<>(new ParameterizedTypeReference<>() {
        });

    }

    /**
     * 将MCP Server注册大模型的回调工具
     *
     * @param mcpClients
     */
    @Autowired(required = false)
    public void setToolCallbackProvider(List<McpAsyncClient> mcpClients) {
        if (!CollectionUtils.isEmpty(mcpClients)) {
            // mcp server provider
            this.toolCallbackProvider = new AsyncMcpToolCallbackProvider(mcpClients);
            log.info("----> 将 MCP Server 注册为大模型的回调工具 <-----");
        }
    }

    /**
     * 没有MCP Server时，使用本地的工具作为大模型的回调工具
     */
    @PostConstruct
    public void initLocalToolCallback() {
        if (this.toolCallbackProvider == null) {
            this.toolCallbackProvider = new StaticToolCallbackProvider(ToolCallbacks.from(new CrawlerTools()));
            log.info("----> 将应用的 CrawlerTools 注册为大模型的回调工具 <-----");
        }
    }

    @Value("${jobclaw.gather.max-output-tokens:8192}")
    private int maxOutputTokens = 8192;
    @Value("${jobclaw.gather.max-batch-records:6}")
    private int maxBatchRecords = 6;
    @Value("${jobclaw.gather.max-input-chars:12000}")
    private int maxInputChars = 12000;
    @Value("${jobclaw.gather.max-requests:128}")
    private int maxRequests = 128;

    /** AI-GENERATED AIDEV-NOTE: 后台文本、表格统一走带来源编号的独立批次。 */
    public List<GatherOcDraftBo> gatherByText(String model, String text) {
        return gatherRecords(model, GatherSourceRecords.text(text)).requireComplete();
    }

    public ExtractionReport<GatherOcDraftBo> gatherRecords(String model, List<SourceRecord> records) {
        var extractor = new StructuredBatchExtractor<>(GatherOcDraftBo.class,
                item -> StringUtils.isNotBlank(item.companyName()) || StringUtils.isNotBlank(item.position()),
                new StructuredBatchExtractor.Limits(maxOutputTokens, maxBatchRecords, maxInputChars, maxRequests));
        var caller = ocAiModelContext.extractionCaller(ModelSelectReq.of(model, GatherModelTypeEnum.CHAT_MODEL));
        var report = extractor.extract(records, "从招聘材料中提取岗位，忠实保留公司、岗位、地点和链接。",
                caller);
        log.info("分批提取：来源={}，请求={}，截断={}，完成={}", records.size(), report.requests(),
                report.truncatedResponses(), report.complete());
        return report;
    }

    // 适用于图片中的数据条目较小的场景，大模型可以一次将结果全部返回
    public List<GatherOcDraftBo> gatherByImg(String model, MimeType type, byte[] bytes) {
        String rid = UUID.randomUUID().toString();
        Media media = Media.builder().mimeType(type)
                .data(bytes)
                .name("图片-" + type.getSubtype() + "-" + rid)
                .id("")
                .build();
        UserMessage msg = UserMessage.builder()
                .media(media)
                .text("提取图片中的表格信息，按照指定要求返回")
                .build();
        ArrayList<GatherOcDraftBo> list = this.ocAiModelContext.chatClient(ModelSelectReq.of(model, GatherModelTypeEnum.IMAGE_MODEL))
                .prompt(new Prompt(msg))
                .tools(new CrawlerTools())
                .call()
                .entity(new ParameterizedTypeReference<ArrayList<GatherOcDraftBo>>() {
                });
        return list;
    }

    /**
     * fixme 说明：文件上传解析依赖模型的多模态能力，DeepSeek V4.1 Flash 支持该方式
     *
     * @param type
     * @param bytes
     * @return
     */
    public List<GatherOcDraftBo> gatherByFile(String model, MimeType type, byte[] bytes) {
        String rid = UUID.randomUUID().toString();
        Media media = Media.builder().mimeType(type)
                .data(bytes)
                .name("文件" + type.getSubtype() + "-" + rid)
                .id(rid)
                .build();
        UserMessage msg = UserMessage.builder()
                .media(media)
                .text("读取给你的文件，按照指定要求返回")
                .build();
        ArrayList<GatherOcDraftBo> list = this.ocAiModelContext.chatClient(ModelSelectReq.of(model, GatherModelTypeEnum.CHAT_MODEL))
                .prompt(new Prompt(msg))
                .tools(new CrawlerTools())
                .call()
                .entity(new ParameterizedTypeReference<ArrayList<GatherOcDraftBo>>() {
                });
        return list;
    }

    /**
     * 基于文本/http链向的网页进行数据提取
     *
     * @param text
     * @return
     */
    public List<GatherOcDraftBo> gatherByAutoSplit(String model, String text) {
        // AI-GENERATED AIDEV-NOTE: 已取得的 HTML 在本地按表格行拆分，模型不负责续写碎片。
        return gatherByText(model, text);
    }


    /**
     * 适用于图片内容较多，返回结果被截断的场景
     *
     * @param type
     * @param bytes
     * @return
     */
    public List<GatherOcDraftBo> gatherByImgAutoSplit(String model, MimeType type, byte[] bytes) {
        String rid = UUID.randomUUID().toString();
        Media media = Media.builder().mimeType(type)
                .data(bytes)
                .name("图片-" + type.getSubtype() + "-" + rid)
                .id("")
                .build();
        return autoContinueChat(model, media, "提取图片中的表格信息，按照指定要求返回");
    }

    /**
     * 针对大模型响应结果截断的场景，进行多轮对话，尝试获取完整的返回
     * 实现原理：基于 chatModel, 借助 ChatMemory 自动实现多轮对话，
     */
    private List<GatherOcDraftBo> autoContinueChat(String model, Media media, String text) {
        // 创建 memory 实例，保存上下文
        ChatMemory chatMemory = MessageWindowChatMemory.builder().maxMessages(MAX_CHAT_CNT).build();
        String chatId = RandomUtil.randomString(6);

        SystemMessage systemMessage = new SystemMessage(OcChatModelApi.GATHER_SYSTEM_PROMPT);
        chatMemory.add(chatId, systemMessage);

        // 选择具体交互的大模型
        Pair<ChatModel, String> modelPair = ocAiModelContext.model(ModelSelectReq.of(model, media == null ? GatherModelTypeEnum.CHAT_MODEL : GatherModelTypeEnum.IMAGE_MODEL));

        List<String> itemList = new ArrayList<>();
        StringBuilder remain = new StringBuilder();
        int cnt = 0;
        while (true) {
            log.info("{}#第{}次大模型数据解析", chatId, cnt + 1);
            UserMessage msg;
            if (cnt == 0) {
                UserMessage.Builder builder = UserMessage.builder()
                        .text(new PromptTemplate("{text}.{format}")
                                .render(Map.of("text", text, "format", gatherResConverter.getFormat()))
                        );
                if (media != null) {
                    builder.media(media);
                }
                msg = builder.build();
            } else {
                msg = new UserMessage("你之前返回的结果不完整，继续返回剩余的内容");
            }
            chatMemory.add(chatId, msg);

            // 工具
            ChatOptions chatOptions = ToolCallingChatOptions.builder()
                    .model(modelPair.getSecond())
                    // 注册给大模型回调的工具
                    .toolCallbacks(toolCallbackProvider.getToolCallbacks())
                    .build();
            try {
                Prompt query = new Prompt(chatMemory.get(chatId), chatOptions);
                if (log.isDebugEnabled()) {
                    // 一行显示日志
                    log.debug("{}#req: {}", chatId, StringUtils.replaceChars(query.toString(), "\n", ""));
                }
                ChatResponse response = modelPair.getFirst().call(query);
                AssistantMessage assistantMessage = response.getResult().getOutput();
                if (log.isDebugEnabled()) {
                    // 一行显示和日志
                    log.debug("{}#res: {}", chatId, StringUtils.replaceChars(assistantMessage.toString(), "\n", ""));
                }

                chatMemory.add(chatId, assistantMessage);
                cnt += 1;

                String outText = assistantMessage.getText().trim();
                itemList.addAll(GatherResFormat.extract(remain, outText));
                if (outText.endsWith("```") || cnt >= MAX_CHAT_CNT) {
                    // 做一个次数限制，避免死循环的调用大模型
                    log.info("{}#经过{}论对话，完成大模型调用", chatId, cnt);
                    break;
                }
                if (cnt > 1 && outText.startsWith("```json")) {
                    int jsonBeginIndex = outText.indexOf("[");
                    if (jsonBeginIndex > 0 && jsonBeginIndex < 15) {
                        // 表示大模型又重新返回了完整的数据，为了避免大模型总是重复解析，我们直接退出循环
                        log.info("{}#大模型重复返回完整解析数据，主动退出多轮对话", chatId);
                        break;
                    }
                }
            } catch (Exception e) {
                // 避免因为多次调用模型出现异常，导致前面获取的数据被丢掉，我们直接跳出来，将已经解析的结果保存下来
                log.error("{}#gather error", chatId, e);
                break;
            }
        }

        if (itemList.isEmpty()) {
            return List.of();
        }

        return itemList.stream().map(this::toBo).filter(Objects::nonNull).collect(Collectors.toList());
    }

    private GatherOcDraftBo toBo(String item) {
        try {
            return JsonUtil.toObj(item, GatherOcDraftBo.class);
        } catch (Exception e) {
            log.warn("解析异常: {}", item, e);
        }
        return null;
    }


    /**
     * 提供给大模型的 function tools
     */
    public class CrawlerTools {
        /**
         * 获取http地址中的表格
         * <p>
         * 说明：即便我给大模型的是一个http链接，但是无法保证大模型每次都会触发调用这个方法(😂)
         *
         * @param url
         * @return
         */
        @Tool(description = "输入一个http链接，返回这个http链接对应的网页中的表格内容")
        public String crawlerHttpTable(@ToolParam(description = "http格式的url地址") String url) {
            log.info("开始获取表格内容: {}", url);
            String text = HttpUtil.get(url, CharsetUtil.CHARSET_UTF_8);
            Document document = Jsoup.parse(text);
            Element table = document.select("table").first();
            String ans = table == null ? document.body().text() : table.html().trim();
            if (log.isDebugEnabled()) {
                // 一行打印
                log.debug("获取到的表格内容为：{}", ans.replaceAll("\n", ""));
            }
            return ans;
        }

        @Tool(description = "将给入的文件内容转换为文本返回")
        public String readFileContent(@ToolParam(description = "文件路径") byte[] bytes) {
            log.info("将给入的数据转换为文本返回");
            return new String(bytes);
        }
    }

}
