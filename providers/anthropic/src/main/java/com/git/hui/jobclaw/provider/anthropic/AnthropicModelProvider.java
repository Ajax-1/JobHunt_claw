package com.git.hui.jobclaw.provider.anthropic;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.AnthropicClientAsync;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClientAsync;
import com.git.hui.jobclaw.core.providers.ModelConfig;
import com.git.hui.jobclaw.core.providers.ModelProvider;
import com.git.hui.jobclaw.core.utils.SpringUtil;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.observation.ChatModelObservationConvention;
import org.springframework.ai.model.Model;
import org.springframework.ai.model.tool.DefaultToolExecutionEligibilityPredicate;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionEligibilityPredicate;
import org.springframework.beans.factory.ObjectProvider;

/**
 * @date 2026/4/10
 */
public class AnthropicModelProvider implements ModelProvider {
    @Override
    public String apiStyle() {
        return "anthropic";
    }

    private final ToolCallingManager toolCallingManager;
    private final ObjectProvider<ObservationRegistry> observationRegistry;
    private final ObjectProvider<ToolExecutionEligibilityPredicate> anthropicToolExecutionEligibilityPredicate;

    public AnthropicModelProvider(ToolCallingManager toolCallingManager,
                                  ObjectProvider<ObservationRegistry> observationRegistry,
                                  ObjectProvider<ToolExecutionEligibilityPredicate> anthropicToolExecutionEligibilityPredicate) {
        this.toolCallingManager = toolCallingManager;
        this.observationRegistry = observationRegistry;
        this.anthropicToolExecutionEligibilityPredicate = anthropicToolExecutionEligibilityPredicate;
    }

    @Override
    public Model model(ModelConfig.ModelInfo info) {
        return switch (info.getType()) {
            case TEXT -> buildChatModel(info);
            case VISION -> buildChatModel(info);
            default -> throw new IllegalArgumentException("Unsupported model type: " + info.getType());
        };
    }

    private ChatModel buildChatModel(ModelConfig.ModelInfo info) {
        var options = AnthropicChatOptions.builder()
                .model(info.getModelName())
                .maxTokens(info.getMaxTokens())
                .apiKey(info.getApiKey())
                .build();


        var backend = new AnthropicClaudeCodeBackend();
        var chatModel = AnthropicChatModel.builder()
                .anthropicClient(anthropicClient(options, backend))
                .anthropicClientAsync(anthropicClientAsync(options, backend))
                .options(options)
                .toolCallingManager(toolCallingManager)
                .observationRegistry(observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP))
                .toolExecutionEligibilityPredicate(anthropicToolExecutionEligibilityPredicate
                        .getIfUnique(DefaultToolExecutionEligibilityPredicate::new))
                .build();

        if (SpringUtil.getBean(ChatModelObservationConvention.class) != null) {
            chatModel.setObservationConvention(SpringUtil.getBean(ChatModelObservationConvention.class));
        }
        return chatModel;
    }

    private static AnthropicClient anthropicClient(AnthropicChatOptions options, AnthropicClaudeCodeBackend backend) {
        var clientBuilder = AnthropicOkHttpClient.builder().backend(backend);
        if (options.getTimeout() != null) clientBuilder.timeout(options.getTimeout());
        if (options.getMaxRetries() != null) clientBuilder.maxRetries(options.getMaxRetries());
        if (options.getProxy() != null) clientBuilder.proxy(options.getProxy());
        return clientBuilder.build();
    }

    private static AnthropicClientAsync anthropicClientAsync(AnthropicChatOptions options, AnthropicClaudeCodeBackend backend) {
        var asyncClientBuilder = AnthropicOkHttpClientAsync.builder().backend(backend);
        if (options.getTimeout() != null) asyncClientBuilder.timeout(options.getTimeout());
        if (options.getMaxRetries() != null) asyncClientBuilder.maxRetries(options.getMaxRetries());
        if (options.getProxy() != null) asyncClientBuilder.proxy(options.getProxy());
        return asyncClientBuilder.build();
    }
}
