package com.git.hui.jobclaw.core.monitor.del;

import com.git.hui.jobclaw.core.monitor.LlmCallContext;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

import java.util.function.Function;
import java.util.function.Supplier;


/**
 * LlmMonitor 部署在 LlmCaller 层，通过 monitor().call() 包裹整个 ChatClient.call() 调用；LlmCaller 必须感知到 LlmMonitor，违背了关注点分离原则。
 *
 * 改用 MonitorMiddleware 的方式来替换，直接通过实现 ReActAdvisor 中预留的钩子，从而实现整体的大模型调用监控
 */
@Deprecated
public interface LlmMonitor {
    <T> T call(LlmCallContext context, Prompt prompt, Supplier<T> supplier);

    <T> Flux<T> stream(LlmCallContext context, Prompt prompt, Supplier<Flux<ChatResponse>> supplier,
                       Function<ChatResponse, T> mapper);

    ChatResponse directCall(Prompt prompt, Supplier<ChatResponse> supplier);
}
