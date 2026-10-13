package com.git.hui.jobclaw.core;

import com.git.hui.jobclaw.core.agent.BizAgent;
import com.git.hui.jobclaw.core.agent.memory.ContextWindowProperties;
import com.git.hui.jobclaw.core.agent.memory.FileSystemChatMemoryRepository;
import com.git.hui.jobclaw.core.channel.ChannelRegistry;
import com.git.hui.jobclaw.core.router.intent.AgentRegistry;
import com.git.hui.jobclaw.core.router.intent.impl.DefaultAgentRegistry;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.model.SpringAIModelProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 *
 * @date 2026/4/8
 */
@Configuration
@ComponentScan("com.git.hui.jobclaw")
@EnableConfigurationProperties(ContextWindowProperties.class)
public class JobClawConfiguration {

    @Bean
    @ConditionalOnProperty(name = SpringAIModelProperties.CHAT_MODEL, havingValue = "unknown", matchIfMissing = true)
    public ChatModel chatModel() {
        return prompt -> new ChatResponse(List.of(new Generation(new AssistantMessage(
                "No AI model has been configured. If you did configure a model recently, restart JobClaw manually for the changes to take effect."))));
    }

    @Bean
    public ChatClient.Builder chatClientBuilder(ObjectProvider<ChatModel> chatModelProvider) {
        ChatModel chatModel = chatModelProvider.getIfUnique(() -> prompt -> new ChatResponse(List.of(new Generation(new AssistantMessage(
                "No AI model has been configured. If you did configure a model recently, restart JobClaw manually for the changes to take effect.")))));
        return ChatClient.builder(chatModel);
    }

    @Bean
    public ChannelRegistry channelRegistry() {
        return new ChannelRegistry();
    }


    @Bean
    public ChatMemory chatMemory(FileSystemChatMemoryRepository chatMemoryRepository) {
        return MessageWindowChatMemory.builder().chatMemoryRepository(chatMemoryRepository).build();
    }


    @Bean
    public AgentRegistry agentRegistry(List<BizAgent> agents) {
        AgentRegistry agentRegistry = new DefaultAgentRegistry();
        agentRegistry.registerAll(agents);
        return agentRegistry;
    }
}
