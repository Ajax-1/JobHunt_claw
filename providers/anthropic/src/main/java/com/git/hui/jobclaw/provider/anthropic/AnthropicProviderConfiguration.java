package com.git.hui.jobclaw.provider.anthropic;

import com.git.hui.jobclaw.core.providers.ModelProvider;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionEligibilityPredicate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Anthropic 模型提供者
 * @date 2026/4/9
 */
@AutoConfiguration
public class AnthropicProviderConfiguration {

    @Bean
    public ModelProvider AnthropicModelProvider(ToolCallingManager toolCallingManager,
                                                ObjectProvider<ObservationRegistry> observationRegistry,
                                                ObjectProvider<ToolExecutionEligibilityPredicate> anthropicToolExecutionEligibilityPredicate) {
        return new AnthropicModelProvider(toolCallingManager,
                observationRegistry,
                anthropicToolExecutionEligibilityPredicate);
    }
}
