package com.git.hui.jobclaw.gather.service.ai.impl.deepseek;

import com.git.hui.jobclaw.constants.gather.GatherModelEnum;
import com.git.hui.jobclaw.core.providers.ModelConfig;
import com.git.hui.jobclaw.core.providers.ModelProviders;
import com.git.hui.jobclaw.gather.service.ai.OcChatModelApi;
import com.git.hui.jobclaw.gather.service.ai.impl.AbsOcChatModelApi;
import io.modelcontextprotocol.client.McpAsyncClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.mcp.AsyncMcpToolCallbackProvider;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.Collections;
import java.util.List;

/**
 * DeepSeek 采集链路适配：统一走 ModelProviders 解析，默认 DeepSeek V4.1 Flash（文本 + 多模态）
 *
 * AI-GENERATED
 * AIDEV-NOTE: 采集侧主模型适配器，替代原智谱专用实现；每次调用重新解析，避免后台改 Key 后拿到旧实例
 *
 */
@Slf4j
@Component
public class DeepSeekOcChatModel extends AbsOcChatModelApi {
    private final ModelProviders modelProviders;

    private final List<McpAsyncClient> mcpClients;

    public DeepSeekOcChatModel(ModelProviders modelProviders, List<McpAsyncClient> mcpClients) {
        this.modelProviders = modelProviders;
        this.mcpClients = mcpClients;
    }

    @Override
    public GatherModelEnum modelEnum() {
        return GatherModelEnum.DEEPSEEK;
    }

    @Override
    public ChatClient chatClient() {
        return buildClient(ModelConfig.ModelType.TEXT);
    }

    @Override
    public ChatClient imgClient() {
        return buildClient(ModelConfig.ModelType.VISION);
    }

    @Override
    public ChatModel chatModel() {
        return resolveChatModel(ModelConfig.ModelType.TEXT);
    }

    @Override
    public String chatModelName() {
        return resolveModelName(ModelConfig.ModelType.TEXT);
    }

    @Override
    public String imgModelName() {
        return resolveModelName(ModelConfig.ModelType.VISION);
    }

    /**
     * 解析默认偏好模型，构建带系统提示词与 MCP 工具的 ChatClient
     */
    private ChatClient buildClient(ModelConfig.ModelType type) {
        ChatClient.Builder builder = ChatClient.builder(resolveChatModel(type))
                .defaultSystem(OcChatModelApi.GATHER_SYSTEM_PROMPT)
                .defaultOptions(ChatOptions.builder().stopSequences(Collections.emptyList()).build())
                .defaultAdvisors(new SimpleLoggerAdvisor());
        if (!CollectionUtils.isEmpty(mcpClients)) {
            // 将 MCP Client 注册为工具，无 MCP 时降级为纯模型调用
            builder.defaultToolCallbacks(new AsyncMcpToolCallbackProvider(mcpClients));
        }
        return builder.build();
    }

    private ChatModel resolveChatModel(ModelConfig.ModelType type) {
        String selection = modelProviders.getDefaultModelSelection(type);
        log.info("当前选中的模型为：{}", selection);
        return (ChatModel) modelProviders.getGlobalModel(selection, type);
    }

    private String resolveModelName(ModelConfig.ModelType type) {
        return modelProviders.getGlobalModelInfo(modelProviders.getDefaultModelSelection(type)).getModelName();
    }
}
