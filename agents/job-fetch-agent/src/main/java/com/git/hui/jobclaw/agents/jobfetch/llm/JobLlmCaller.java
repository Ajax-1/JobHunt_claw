package com.git.hui.jobclaw.agents.jobfetch.llm;

import com.git.hui.jobclaw.core.agent.llm.SimpleLlmCaller;
import com.git.hui.jobclaw.core.agent.models.UserConversationInfo;
import com.git.hui.jobclaw.core.providers.ModelConfig;
import com.git.hui.jobclaw.core.providers.ModelProviders;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;

/**
 *
 * @date 2026/4/18
 */
@Component
public class JobLlmCaller extends SimpleLlmCaller {

    public JobLlmCaller(ModelProviders modelProviders) {
        super(modelProviders);
    }

    public ChatResponse response(UserConversationInfo user, Prompt prompt) {
        return getClient(user, prompt).prompt(prompt).call().chatResponse();
    }

    public ChatClient simpleClient(UserConversationInfo user) {
        var chatModel = (ChatModel) modelProviders.getModel(user.jobClawUserId(), ModelConfig.ModelType.TEXT);
        return ChatClient.builder(chatModel)
                // todo 需要在这里添加统一的 token 记录 advisor
                .build();
    }

}
