package com.git.hui.jobclaw.core.agent.llm;

import com.git.hui.jobclaw.core.agent.models.UserConversationInfo;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

import java.util.function.Function;

/**
 * 大模型调用接口
 * @date 2026/4/23
 */
public interface LlmCaller {
    <T> T call(UserConversationInfo user, Prompt prompt, Class<T> clz);

    String call(UserConversationInfo user, Prompt prompt);

    Flux<String> stream(UserConversationInfo user, Prompt prompt);

    <T> Flux<T> stream(UserConversationInfo user, Prompt prompt, Function<ChatResponse, T> func);
}
