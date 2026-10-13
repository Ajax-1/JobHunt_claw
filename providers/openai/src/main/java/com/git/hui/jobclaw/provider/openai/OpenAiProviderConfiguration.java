package com.git.hui.jobclaw.provider.openai;

import com.git.hui.jobclaw.core.providers.ModelProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * openai 模型提供者
 * @date 2026/4/9
 */
@AutoConfiguration
public class OpenAiProviderConfiguration {

    @Bean
    public ModelProvider openAiModelProvider(ObjectProvider<RestClient.Builder> restClientBuilderProvider,
                                             ObjectProvider<WebClient.Builder> webClientBuilderProvider,
                                             ObjectProvider<ResponseErrorHandler> responseErrorHandler) {
        return new OpenAiModelProvider(restClientBuilderProvider, webClientBuilderProvider, responseErrorHandler);
    }
}
