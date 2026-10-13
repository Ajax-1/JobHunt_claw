package com.git.hui.jobclaw.provider.ali;

import com.alibaba.cloud.ai.tool.validator.ToolCallValidator;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionEligibilityPredicate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

/**
 *
 * @date 2026/4/10
 */
@AutoConfiguration
public class AliProviderConfiguration {
    @Bean
    public AliModelProvider aliModelProvider(ToolCallingManager toolCallingManager,
                                             ObjectProvider<ResponseErrorHandler> responseErrorHandler, ObjectProvider<ObservationRegistry> observationRegistry, ObjectProvider<WebClient.Builder> webClientBuilderProvider, ObjectProvider<RestClient.Builder> restClientBuilderProvider, ObjectProvider<ToolExecutionEligibilityPredicate> dashscopeToolExecutionEligibilityPredicate, ObjectProvider<ToolCallValidator> toolCallValidatorProvider) {
        return new AliModelProvider(toolCallingManager,
                responseErrorHandler,
                observationRegistry,
                webClientBuilderProvider,
                restClientBuilderProvider,
                dashscopeToolExecutionEligibilityPredicate,
                toolCallValidatorProvider);
    }
}
