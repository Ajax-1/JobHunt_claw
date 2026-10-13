package com.git.hui.jobclaw.provider.openai;

import com.git.hui.jobclaw.core.providers.ModelConfig;
import com.git.hui.jobclaw.core.providers.ModelProvider;
import com.git.hui.jobclaw.core.utils.SpringUtil;
import io.micrometer.common.util.StringUtils;
import org.springframework.ai.chat.observation.ChatModelObservationConvention;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.observation.EmbeddingModelObservationConvention;
import org.springframework.ai.image.observation.ImageModelObservationConvention;
import org.springframework.ai.model.Model;
import org.springframework.ai.model.SimpleApiKey;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.OpenAiImageModel;
import org.springframework.ai.openai.OpenAiImageOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.openai.api.OpenAiImageApi;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

/**
 *
 * @date 2026/4/9
 */
public class OpenAiModelProvider implements ModelProvider {
    private ObjectProvider<RestClient.Builder> restClientBuilderProvider;
    private ObjectProvider<WebClient.Builder> webClientBuilderProvider;
    private ObjectProvider<ResponseErrorHandler> responseErrorHandler;

    public OpenAiModelProvider(ObjectProvider<RestClient.Builder> restClientBuilderProvider, ObjectProvider<WebClient.Builder> webClientBuilderProvider, ObjectProvider<ResponseErrorHandler> responseErrorHandler) {
        this.restClientBuilderProvider = restClientBuilderProvider;
        this.webClientBuilderProvider = webClientBuilderProvider;
        this.responseErrorHandler = responseErrorHandler;
    }

    @Override
    public String apiStyle() {
        return "openai";
    }

    @Override
    public Model model(ModelConfig.ModelInfo info) {
        return switch (info.getType()) {
            case TEXT -> buildChatModel(info);
            case VISION -> buildVisionModel(info);
            case IMAGE -> buildImageModel(info);
            case EMBEDDING -> buildEmbeddingModel(info);
            case VIDEO -> throw new IllegalArgumentException("unsupported model type: " + info.getType());
            case ASR -> throw new IllegalArgumentException("unsupported model type: " + info.getType());
            case TTS -> throw new IllegalArgumentException("unsupported model type: " + info.getType());
        };
    }

    private Model buildChatModel(ModelConfig.ModelInfo info) {
        var builder = OpenAiApi.builder()
                .apiKey(info.getApiKey())
                .restClientBuilder(restClientBuilderProvider.getIfAvailable(RestClient::builder))
                .webClientBuilder(webClientBuilderProvider.getIfAvailable(WebClient::builder))
                .responseErrorHandler(responseErrorHandler.getIfAvailable(() -> RetryUtils.DEFAULT_RESPONSE_ERROR_HANDLER));
        if (StringUtils.isNotBlank(info.getBaseUrl())) {
            builder.baseUrl(info.getBaseUrl());
        }
        if (StringUtils.isNotBlank(info.getPath())) {
            builder.completionsPath(info.getPath());
        }
        OpenAiApi openAiApi = builder.build();

        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(OpenAiChatOptions.builder().model(info.getModelName()).maxTokens(info.getMaxTokens()).build())
                .build();
        if (SpringUtil.getBeanOrNull(ChatModelObservationConvention.class) != null) {
            chatModel.setObservationConvention(SpringUtil.getBean(ChatModelObservationConvention.class));
        }
        return chatModel;
    }


    private Model buildVisionModel(ModelConfig.ModelInfo info) {
        var builder = OpenAiApi.builder()
                .apiKey(info.getApiKey())
                .restClientBuilder(restClientBuilderProvider.getIfAvailable(RestClient::builder))
                .webClientBuilder(webClientBuilderProvider.getIfAvailable(WebClient::builder))
                .responseErrorHandler(responseErrorHandler.getIfAvailable(() -> RetryUtils.DEFAULT_RESPONSE_ERROR_HANDLER));
        if (StringUtils.isNotBlank(info.getBaseUrl())) {
            builder.baseUrl(info.getBaseUrl());
        }
        if (StringUtils.isNotBlank(info.getPath())) {
            builder.completionsPath(info.getPath());
        }
        OpenAiApi openAiApi = builder.build();
        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(OpenAiChatOptions.builder().model(info.getModelName()).build())
                .build();
        if (SpringUtil.getBeanOrNull(ChatModelObservationConvention.class) != null) {
            chatModel.setObservationConvention(SpringUtil.getBean(ChatModelObservationConvention.class));
        }
        return chatModel;
    }

    private OpenAiEmbeddingModel buildEmbeddingModel(ModelConfig.ModelInfo info) {
        var builder = OpenAiApi.builder()
                .apiKey(info.getApiKey())
                .restClientBuilder(restClientBuilderProvider.getIfAvailable(RestClient::builder))
                .webClientBuilder(webClientBuilderProvider.getIfAvailable(WebClient::builder))
                .responseErrorHandler(responseErrorHandler.getIfAvailable(() -> RetryUtils.DEFAULT_RESPONSE_ERROR_HANDLER));
        if (StringUtils.isNotBlank(info.getBaseUrl())) {
            builder.baseUrl(info.getBaseUrl());
        }
        if (StringUtils.isNotBlank(info.getPath())) {
            builder.embeddingsPath(info.getPath());
        }

        OpenAiApi openAiApi = builder.build();
        OpenAiEmbeddingModel embeddingModel = new OpenAiEmbeddingModel(openAiApi,
                MetadataMode.EMBED,
                OpenAiEmbeddingOptions.builder().model(info.getModelName()).build());
        if (SpringUtil.getBeanOrNull(EmbeddingModelObservationConvention.class) != null) {
            embeddingModel.setObservationConvention(SpringUtil.getBean(EmbeddingModelObservationConvention.class));
        }
        return embeddingModel;
    }

    private OpenAiImageModel buildImageModel(ModelConfig.ModelInfo info) {
        var builder = OpenAiImageApi.builder()
                .apiKey(info.getApiKey())
                .restClientBuilder(restClientBuilderProvider.getIfAvailable(RestClient::builder))
                .responseErrorHandler(responseErrorHandler.getIfAvailable(() -> RetryUtils.DEFAULT_RESPONSE_ERROR_HANDLER));
        if (StringUtils.isNotBlank(info.getBaseUrl())) {
            builder.baseUrl(info.getBaseUrl());
        }
        if (StringUtils.isNotBlank(info.getPath())) {
            builder.imagesPath(info.getPath());
        }

        OpenAiImageApi openAiImageApi = builder.build();

        OpenAiImageModel imageModel = new OpenAiImageModel(openAiImageApi,
                OpenAiImageOptions.builder().model(info.getModelName()).build(),
                RetryUtils.DEFAULT_RETRY_TEMPLATE);
        if (SpringUtil.getBeanOrNull(ImageModelObservationConvention.class) != null) {
            imageModel.setObservationConvention(SpringUtil.getBean(ImageModelObservationConvention.class));
        }
        return imageModel;
    }
}
