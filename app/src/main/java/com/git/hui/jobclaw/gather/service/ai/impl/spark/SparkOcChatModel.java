package com.git.hui.jobclaw.gather.service.ai.impl.spark;

import com.git.hui.jobclaw.constants.gather.GatherModelEnum;
import com.git.hui.jobclaw.gather.service.ai.impl.AbsOcChatModelApi;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.Collections;


/**
 * 星火大模型，适配JobClaw
 *
 * @date 2025/7/30
 */
@Component
public class SparkOcChatModel extends AbsOcChatModelApi {

    private final ChatModel chatModel;

    private final ChatClient chatClient;

    public SparkOcChatModel(@Qualifier("sparkLiteModel") ChatModel chatModel) {
        this.chatModel = chatModel;

        this.chatClient = ChatClient.builder(this.chatModel)
                .defaultSystem(GATHER_SYSTEM_PROMPT)
                .defaultOptions(ChatOptions.builder()
                        .model(chatModelName())
                        .stopSequences(Collections.emptyList()).build())
                .defaultAdvisors(new SimpleLoggerAdvisor())
                .build();
    }

    @Override
    public GatherModelEnum modelEnum() {
        return GatherModelEnum.SPARK_LITE;
    }

    @Override
    public ChatClient chatClient() {
        return chatClient;
    }

    @Override
    public ChatModel chatModel() {
        return chatModel;
    }

    @Override
    public String chatModelName() {
        return chatModel.getDefaultOptions().getModel();
    }
}
