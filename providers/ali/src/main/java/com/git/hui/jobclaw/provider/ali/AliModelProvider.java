package com.git.hui.jobclaw.provider.ali;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.api.DashScopeAudioSpeechApi;
import com.alibaba.cloud.ai.dashscope.api.DashScopeAudioTranscriptionApi;
import com.alibaba.cloud.ai.dashscope.api.DashScopeImageApi;
import com.alibaba.cloud.ai.dashscope.audio.transcription.DashScopeAudioTranscriptionModel;
import com.alibaba.cloud.ai.dashscope.audio.transcription.DashScopeAudioTranscriptionOptions;
import com.alibaba.cloud.ai.dashscope.audio.tts.DashScopeAudioSpeechModel;
import com.alibaba.cloud.ai.dashscope.audio.tts.DashScopeAudioSpeechOptions;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.dashscope.embedding.DashScopeEmbeddingModel;
import com.alibaba.cloud.ai.dashscope.embedding.DashScopeEmbeddingOptions;
import com.alibaba.cloud.ai.dashscope.image.DashScopeImageModel;
import com.alibaba.cloud.ai.dashscope.image.DashScopeImageOptions;
import com.alibaba.cloud.ai.tool.validator.DefaultToolCallValidator;
import com.alibaba.cloud.ai.tool.validator.ToolCallValidator;
import com.git.hui.jobclaw.core.providers.ModelConfig;
import com.git.hui.jobclaw.core.providers.ModelProvider;
import com.git.hui.jobclaw.core.utils.SpringUtil;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.chat.observation.ChatModelObservationConvention;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.observation.EmbeddingModelObservationConvention;
import org.springframework.ai.image.observation.ImageModelObservationConvention;
import org.springframework.ai.model.Model;
import org.springframework.ai.model.SimpleApiKey;
import org.springframework.ai.model.tool.DefaultToolExecutionEligibilityPredicate;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionEligibilityPredicate;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Objects;

/**
 *
 * @date 2026/4/10
 */
public class AliModelProvider implements ModelProvider {

    private final ToolCallingManager toolCallingManager;
    private final ObjectProvider<ResponseErrorHandler> responseErrorHandler;
    private final ObjectProvider<ObservationRegistry> observationRegistry;
    private final ObjectProvider<WebClient.Builder> webClientBuilderProvider;
    private final ObjectProvider<RestClient.Builder> restClientBuilderProvider;
    private final ObjectProvider<ToolExecutionEligibilityPredicate> dashscopeToolExecutionEligibilityPredicate;
    private final ObjectProvider<ToolCallValidator> toolCallValidatorProvider;

    public AliModelProvider(ToolCallingManager toolCallingManager, ObjectProvider<ResponseErrorHandler> responseErrorHandler, ObjectProvider<ObservationRegistry> observationRegistry, ObjectProvider<WebClient.Builder> webClientBuilderProvider, ObjectProvider<RestClient.Builder> restClientBuilderProvider, ObjectProvider<ToolExecutionEligibilityPredicate> dashscopeToolExecutionEligibilityPredicate, ObjectProvider<ToolCallValidator> toolCallValidatorProvider) {
        this.toolCallingManager = toolCallingManager;
        this.responseErrorHandler = responseErrorHandler;
        this.observationRegistry = observationRegistry;
        this.webClientBuilderProvider = webClientBuilderProvider;
        this.restClientBuilderProvider = restClientBuilderProvider;
        this.dashscopeToolExecutionEligibilityPredicate = dashscopeToolExecutionEligibilityPredicate;
        this.toolCallValidatorProvider = toolCallValidatorProvider;
    }

    @Override
    public String apiStyle() {
        return "ali";
    }

    @Override
    public Model model(ModelConfig.ModelInfo info) {
        return switch (info.getType()) {
            case TEXT -> buildChatModel(info);
            case VISION -> buildChatModel(info);
            case IMAGE -> buildImageModel(info);
            case EMBEDDING -> buildEmbeddingModel(info);
            case VIDEO -> throw new IllegalArgumentException("unsupported model type: " + info.getType());
            case ASR -> buildAsrModel(info);
            case TTS -> buildTtsModel(info);
        };
    }


    private DashScopeChatModel buildChatModel(ModelConfig.ModelInfo modelInfo) {
        DashScopeApi dashscopeApi =
                DashScopeApi.builder()
                        .apiKey(modelInfo.getApiKey())
                        .workSpaceId(null)
                        .baseUrl(modelInfo.getBaseUrl())
                        .completionsPath(modelInfo.getPath())
                        .restClientBuilder(restClientBuilderProvider.getIfAvailable(RestClient::builder))
                        .webClientBuilder(webClientBuilderProvider.getIfAvailable(WebClient::builder))
                        .responseErrorHandler(responseErrorHandler.getIfAvailable(() -> RetryUtils.DEFAULT_RESPONSE_ERROR_HANDLER))
                        .build();


        DashScopeChatModel dashscopeModel = DashScopeChatModel.builder().dashScopeApi(dashscopeApi)
                .toolCallingManager(toolCallingManager)
                .defaultOptions(DashScopeChatOptions.builder().model(modelInfo.getModelName()).build())
                .observationRegistry(observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP))
                .toolExecutionEligibilityPredicate(dashscopeToolExecutionEligibilityPredicate.getIfUnique(
                        DefaultToolExecutionEligibilityPredicate::new))
                .toolCallValidator(toolCallValidatorProvider.getIfUnique(DefaultToolCallValidator::new))
                .build();

        if (SpringUtil.getBeanOrNull(ChatModelObservationConvention.class) != null) {
            dashscopeModel.setObservationConvention(SpringUtil.getBean(ChatModelObservationConvention.class));
        }
        return dashscopeModel;
    }

    private DashScopeImageModel buildImageModel(ModelConfig.ModelInfo modelInfo) {
        DashScopeImageApi dashScopeImageApi = DashScopeImageApi.builder()
                .apiKey(modelInfo.getApiKey())
                .baseUrl(modelInfo.getBaseUrl())
                .restClientBuilder(restClientBuilderProvider.getIfAvailable(RestClient::builder))
                .responseErrorHandler(responseErrorHandler.getIfAvailable(() -> RetryUtils.DEFAULT_RESPONSE_ERROR_HANDLER))
                .build();

        DashScopeImageModel dashScopeImageModel = DashScopeImageModel
                .builder()
                .dashScopeApi(dashScopeImageApi)
                .defaultOptions(DashScopeImageOptions.builder().model(modelInfo.getModelName()).build())
                .observationRegistry(observationRegistry.getIfUnique(() -> {
                    return ObservationRegistry.NOOP;
                })).build();
        Objects.requireNonNull(dashScopeImageModel);
        if (SpringUtil.getBeanOrNull(ImageModelObservationConvention.class) != null) {
            dashScopeImageModel.setObservationConvention(SpringUtil.getBean(ImageModelObservationConvention.class));
        }
        return dashScopeImageModel;
    }

    private DashScopeEmbeddingModel buildEmbeddingModel(ModelConfig.ModelInfo modelInfo) {

        DashScopeApi dashscopeApi =
                DashScopeApi.builder()
                        .apiKey(modelInfo.getApiKey())
                        .baseUrl(modelInfo.getBaseUrl())
                        .embeddingsPath(modelInfo.getPath())
                        .restClientBuilder(restClientBuilderProvider.getIfAvailable(RestClient::builder))
                        .webClientBuilder(webClientBuilderProvider.getIfAvailable(WebClient::builder))
                        .responseErrorHandler(responseErrorHandler.getIfAvailable(() -> RetryUtils.DEFAULT_RESPONSE_ERROR_HANDLER))
                        .build();

        DashScopeEmbeddingModel embeddingModel = DashScopeEmbeddingModel.builder()
                .dashScopeApi(dashscopeApi)
                .metadataMode(MetadataMode.EMBED)
                .defaultOptions(DashScopeEmbeddingOptions.builder().model(modelInfo.getModelName()).build())
                .observationRegistry(observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP)).build();
        Objects.requireNonNull(embeddingModel);
        if (SpringUtil.getBeanOrNull(EmbeddingModelObservationConvention.class) != null) {
            embeddingModel.setObservationConvention(SpringUtil.getBean(EmbeddingModelObservationConvention.class));
        }
        return embeddingModel;
    }

    private DashScopeAudioSpeechModel buildTtsModel(ModelConfig.ModelInfo modelInfo) {

        DashScopeAudioSpeechApi dashScopeAudioSpeechApi = DashScopeAudioSpeechApi.builder()
                .apiKey(new SimpleApiKey(modelInfo.getApiKey()))
                .restClientBuilder(restClientBuilderProvider.getIfAvailable(RestClient::builder))
                .webClientBuilder(webClientBuilderProvider.getIfAvailable(WebClient::builder))
                .responseErrorHandler(responseErrorHandler.getIfAvailable(() -> RetryUtils.DEFAULT_RESPONSE_ERROR_HANDLER)).build();
        var model = DashScopeAudioSpeechModel.builder().audioSpeechApi(dashScopeAudioSpeechApi)
                .defaultOptions(DashScopeAudioSpeechOptions.builder().model(modelInfo.getModelName()).build())
                .build();
        return model;
    }

    private DashScopeAudioTranscriptionModel buildAsrModel(ModelConfig.ModelInfo modelInfo) {
        DashScopeAudioTranscriptionApi dashScopeAudioTranscriptionApi = DashScopeAudioTranscriptionApi.builder()
                .apiKey(new SimpleApiKey(modelInfo.getApiKey()))
                .restClientBuilder(restClientBuilderProvider.getIfAvailable(RestClient::builder))
                .webClientBuilder(webClientBuilderProvider.getIfAvailable(WebClient::builder))
                .responseErrorHandler(responseErrorHandler.getIfAvailable(() -> RetryUtils.DEFAULT_RESPONSE_ERROR_HANDLER)).build();
        return DashScopeAudioTranscriptionModel.builder()
                .audioTranscriptionApi(dashScopeAudioTranscriptionApi)
                .defaultOptions(DashScopeAudioTranscriptionOptions.builder().model(modelInfo.getModelName()).build())
                .build();
    }
}
