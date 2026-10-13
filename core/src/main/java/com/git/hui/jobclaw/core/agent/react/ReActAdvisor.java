package com.git.hui.jobclaw.core.agent.react;

import com.git.hui.jobclaw.core.utils.SpringUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientMessageAggregator;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.util.CollectionUtils;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 自定义 ReAct (Reasoning-Acting) Advisor
 * <p>
 * 替代 Spring AI 内置的 ToolCallAdvisor，自行控制 reasoning → acting 的完整循环。
 * 核心设计：
 * <ol>
 *   <li>第一次推理通过 Advisor Chain 调用（保留 Memory 等前置 Advisor 的效果）</li>
 *   <li>后续迭代直接调用 ChatModel（避免重复触发 Memory 等 Advisor）</li>
 *   <li>工具通过 ToolCallback.call(args, toolContext) 手动执行，参照 DefaultToolCallingManager
 *       从 ToolCallingChatOptions 构建 ToolContext 并始终传入，确保工具可访问上下文信息</li>
 *   <li>各阶段触发 Middleware 生命周期钩子</li>
 * </ol>
 * <p>
 * AIDEV-NOTE: 使用此 Advisor 时应移除 ToolCallAdvisor，避免工具被重复执行。
 * 需要设置 internalToolExecutionEnabled(false) 以禁用 Spring AI 的自动工具执行。
 *
 * @date 2026/6/5
 */
@Slf4j
public class ReActAdvisor implements CallAdvisor, StreamAdvisor {

    private final ChatModel chatModel;
    private final List<ReActMiddleware> middlewares;
    private final int maxIterations;
    private final int order;

    private ReActAdvisor(Builder builder) {
        this.chatModel = builder.chatModel;
        this.middlewares = builder.middlewares;
        this.maxIterations = builder.maxIterations;
        this.order = builder.order;
    }

    // ==================== CallAdvisor ====================

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        log.debug("[ReAct] adviseCall start, maxIterations={}", maxIterations);
        String chatId = "S-" + UUID.randomUUID();

        // 在第一次推理前通知中间件（确保 setContext 和 beforeReasoning 覆盖首次调用）
        List<Message> initialMessages = new ArrayList<>(request.prompt().getInstructions());
        notifySetContext(request, chatId);
        notifyBeforeReasoning(initialMessages, 0, chatId);

        // 第一次推理走 Advisor Chain（保留 Memory 等前置效果）
        ChatClientResponse firstResponse;
        try {
            firstResponse = chain.nextCall(request);
        } catch (RuntimeException e) {
            notifyError(e, 0, chatId);
            throw e;
        }
        ChatResponse chatResponse = firstResponse.chatResponse();

        if (chatResponse == null || chatResponse.getResult() == null) {
            notifyAfterReasoning(chatResponse, 0, chatId);
            notifyComplete(1, null, chatId);
            return firstResponse;
        }

        AssistantMessage output = chatResponse.getResult().getOutput();
        List<AssistantMessage.ToolCall> toolCalls = output.getToolCalls();
        notifyAfterReasoning(chatResponse, 0, chatId);

        // 无工具调用 → 直接返回
        if (toolCalls == null || toolCalls.isEmpty()) {
            notifyComplete(1, output.getText(), chatId);
            return firstResponse;
        }

        // 进入 ReAct 循环
        List<Message> messages = new ArrayList<>(request.prompt().getInstructions());
        messages.add(output);
        ReactLoopResult loopResult = runReactLoop(messages, toolCalls, request, 1, chatId);
        if (loopResult != null && loopResult.finalResponse != null) {
            notifyComplete(loopResult.iterations(),
                    loopResult.finalResponse.chatResponse().getResult().getOutput().getText(),
                    chatId);
            return firstResponse.mutate()
                    .chatResponse(loopResult.finalResponse.chatResponse())
                    .build();
        }

        log.warn("[ReAct] Max iterations ({}) reached", maxIterations);
        return firstResponse;
    }

    /**
     * 同步执行 ReAct 循环（工具调用本质上是阻塞的）
     *
     * @return ReactLoopResult 包含最终响应、工具执行结果和迭代次数；达到最大迭代次数时返回 null
     */
    private ReactLoopResult runReactLoop(List<Message> messages,
                                         List<AssistantMessage.ToolCall> toolCalls,
                                         ChatClientRequest request,
                                         int startIter, String chatId) {
        List<ToolResponseMessage> allToolResponses = new ArrayList<>();

        for (int iter = startIter; iter < maxIterations; iter++) {
            // ---- Acting: 执行工具 ----
            ToolResponseMessage toolResponses = executeTools(toolCalls, request, iter, chatId);
            if (toolResponses == null) return null;
            allToolResponses.add(toolResponses);
            messages.add(toolResponses);

            // ---- Reasoning: 再次推理 ----
            notifyBeforeReasoning(messages, iter, chatId);
            ChatResponse nextResponse = callModelDirect(messages, request, chatId);
            if (nextResponse == null || nextResponse.getResult() == null) return null;

            AssistantMessage output = nextResponse.getResult().getOutput();
            notifyAfterReasoning(nextResponse, iter, chatId);
            messages.add(output);

            toolCalls = output.getToolCalls();
            if (toolCalls == null || toolCalls.isEmpty()) {
                ChatClientResponse finalResponse = ChatClientResponse.builder()
                        .chatResponse(nextResponse)
                        .context(request.context())
                        .build();
                return new ReactLoopResult(finalResponse, allToolResponses, iter + 1);
            }
        }

        log.warn("[ReAct] Max iterations ({}) reached in loop", maxIterations);
        return null;
    }

    // ==================== StreamAdvisor ====================

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
        log.debug("[ReAct] adviseStream start, maxIterations={}", maxIterations);
        String chatId = "A-" + UUID.randomUUID();

        List<Message> initialMessages = new ArrayList<>(request.prompt().getInstructions());
        notifySetContext(request, chatId);
        notifyBeforeReasoning(initialMessages, 0, chatId);

        return streamWithReAct(request, chain, initialMessages, 0, chatId);
    }

    // ==================== 非阻塞流式 ReAct 核心 ====================

    /**
     * 流式 ReAct 循环：发射首个推理的 chunks（实时 pass-through），
     * 在流结束后聚合检查工具调用，若有则执行工具并递归流式下一次推理。
     */
    private Flux<ChatClientResponse> streamWithReAct(
            ChatClientRequest request,
            StreamAdvisorChain chain,
            List<Message> messages,
            int iteration,
            String chatId) {

        Flux<ChatClientResponse> responseFlux;
        if (iteration == 0) {
            responseFlux = chain.nextStream(request);
        } else {
            responseFlux = callModelDirectStream(messages, request, chatId);
        }

        AtomicReference<ChatClientResponse> aggregatedRef = new AtomicReference<>();

        return responseFlux
                .publish(shared -> {
                    ChatClientMessageAggregator aggregator = new ChatClientMessageAggregator();
                    Flux<ChatClientResponse> streamingBranch = aggregator.aggregateChatClientResponse(
                            shared, aggregatedRef::set);
                    Flux<ChatClientResponse> recursionBranch = Flux.defer(() ->
                            handleAggregatedResponse(aggregatedRef.get(), request, chain, messages, iteration, chatId)
                    ).subscribeOn(Schedulers.boundedElastic());
                    return streamingBranch.concatWith(recursionBranch);
                });
    }

    /**
     * 处理流聚合完成后的响应：检查工具调用，执行工具，递归下一次流式推理
     */
    private Flux<ChatClientResponse> handleAggregatedResponse(
            ChatClientResponse aggregated,
            ChatClientRequest request,
            StreamAdvisorChain chain,
            List<Message> messages,
            int iteration,
            String chatId) {

        if (aggregated == null || aggregated.chatResponse() == null || aggregated.chatResponse().getResult() == null) {
            notifyAfterReasoning(null, iteration, chatId);
            notifyComplete(iteration + 1, null, chatId);
            return Flux.empty();
        }

        ChatResponse chatResponse = aggregated.chatResponse();
        AssistantMessage output = chatResponse.getResult().getOutput();
        List<AssistantMessage.ToolCall> toolCalls = output.getToolCalls();

        notifyAfterReasoning(chatResponse, iteration, chatId);

        // 无工具调用 → 完成
        if (toolCalls == null || toolCalls.isEmpty()) {
            notifyComplete(iteration + 1, output.getText(), chatId);
            return Flux.empty();
        }

        // 检查最大迭代次数
        int nextIter = iteration + 1;
        if (nextIter >= maxIterations) {
            log.warn("[ReAct] Max iterations ({}) reached at iteration {}", maxIterations, iteration);
            notifyComplete(nextIter, output.getText(), chatId);
            return Flux.empty();
        }

        // 执行工具
        ToolResponseMessage toolResponses = executeTools(toolCalls, request, iteration, chatId);
        if (toolResponses == null) {
            return Flux.empty();
        }

        // 构建工具执行结果合成响应（供 LlmRspCell 识别）
        ChatClientResponse toolResultResp = buildToolResultResponse(List.of(toolResponses), request);

        // 构建下一次推理的消息列表
        List<Message> nextMessages = new ArrayList<>(messages);
        nextMessages.add(output);
        nextMessages.add(toolResponses);

        notifyBeforeReasoning(nextMessages, nextIter, chatId);

        // 递归进入下一次流式推理
        Flux<ChatClientResponse> nextFlux = streamWithReAct(request, chain, nextMessages, nextIter, chatId);
        return Flux.just(toolResultResp).concatWith(nextFlux);
    }

    /**
     * 直接调用 ChatModel.stream()（不经过 Advisor Chain），
     * 用于 ReAct 循环中后续迭代的流式推理
     */
    private Flux<ChatClientResponse> callModelDirectStream(
            List<Message> messages,
            ChatClientRequest request,
            String chatId) {

        ChatOptions options = request.prompt().getOptions();
        ToolCallback[] toolArray = resolveToolCallbacks(request);
        ChatOptions directOptions = ToolCallingChatOptions.builder()
                .internalToolExecutionEnabled(false)
                .toolCallbacks(toolArray)
                .build();
        Prompt prompt = new Prompt(messages, directOptions);
        return chatModel.stream(prompt)
                .map(chatResponse -> ChatClientResponse.builder()
                        .chatResponse(chatResponse)
                        .context(request.context())
                        .build());
    }

    // ==================== ReAct 循环核心 ====================

    /**
     * ReAct 循环的结果容器，包含最终响应和工具执行结果
     *
     * @param finalResponse 模型最终响应（无工具调用时的 ChatClientResponse）
     * @param toolResponses 所有迭代的工具执行结果
     * @param iterations    实际迭代次数
     */
    private record ReactLoopResult(ChatClientResponse finalResponse,
                                   List<ToolResponseMessage> toolResponses,
                                   int iterations) {
    }

    /**
     * 构建包含工具执行结果的合成 ChatClientResponse
     * <p>
     * AIDEV-NOTE: 将工具执行结果包装为 AssistantMessage 并设置 "toolResult" 元数据标记，
     * 使其能够流经 LlmRspCell::of 并被正确识别为工具执行结果。
     */
    private ChatClientResponse buildToolResultResponse(List<ToolResponseMessage> toolResponses,
                                                       ChatClientRequest request) {
        var sb = new StringBuilder();
        for (var tr : toolResponses) {
            for (var resp : tr.getResponses()) {
                if (sb.length() > 0) sb.append("\n---\n");
                sb.append("[").append(resp.name()).append("]: ");
                String data = resp.responseData();
                sb.append(data != null && data.length() > 500 ? data.substring(0, 500) + "..." : data);
            }
        }

        AssistantMessage msg = AssistantMessage.builder()
                .content(sb.toString())
                .properties(Map.of("toolResult", true))
                .build();
        ChatResponse chatResponse = new ChatResponse(List.of(new Generation(msg)));
        return ChatClientResponse.builder()
                .chatResponse(chatResponse)
                .context(request.context())
                .build();
    }

    /**
     * 手动执行工具调用：通过 ToolCallback.call(args, toolContext) 逐个执行
     * <p>
     * AIDEV-NOTE: 参照 DefaultToolCallingManager.executeToolCall() 的实现，
     * 始终从 ToolCallingChatOptions 构建 ToolContext 并传入工具调用，
     * 确保工具可以访问请求上下文、用户信息等关键数据。
     */
    private ToolResponseMessage executeTools(List<AssistantMessage.ToolCall> toolCalls,
                                             ChatClientRequest request,
                                             int iter, String chatId) {
        notifyBeforeActing(toolCalls, iter, chatId);
        log.info("[ReAct] Iteration {}: executing {} tool(s)", iter, toolCalls.size());

        try {
            ToolCallback[] callbacks = resolveToolCallbacks(request);
            // 参照 DefaultToolCallingManager.buildToolContext()，从 ChatOptions 构建 ToolContext
            ToolContext toolContext = buildToolContext(request.prompt());
            List<ToolResponseMessage.ToolResponse> responses = new ArrayList<>();

            for (var toolCall : toolCalls) {
                String toolName = toolCall.name();
                String toolArgs = toolCall.arguments();
                String toolCallId = toolCall.id();

                log.info("[ReAct] Calling tool '{}' (id={})", toolName, toolCallId);

                ToolCallback callback = findCallback(toolName, callbacks);
                if (callback == null) {
                    String errorMsg = String.format("Tool '%s' not found", toolName);
                    log.warn("[ReAct] {}", errorMsg);
                    responses.add(new ToolResponseMessage.ToolResponse(toolCallId, toolName, errorMsg));
                    continue;
                }

                try {
                    // 始终传入 ToolContext，与 DefaultToolCallingManager 保持一致
                    String result = callback.call(toolArgs, toolContext);
                    log.info("[ReAct] Tool '{}' returned {} chars", toolName, result != null ? result.length() : 0);
                    responses.add(new ToolResponseMessage.ToolResponse(toolCallId, toolName,
                            result != null ? result : "(empty)"));
                } catch (Exception e) {
                    log.error("[ReAct] Tool '{}' execution failed", toolName, e);
                    responses.add(new ToolResponseMessage.ToolResponse(toolCallId, toolName,
                            "Tool execution failed: " + e.getMessage()));
                }
            }

            ToolResponseMessage toolResponse = ToolResponseMessage.builder()
                    .responses(responses)
                    .build();

            notifyAfterActing(toolResponse, iter, chatId);
            return toolResponse;

        } catch (Exception e) {
            log.error("[ReAct] Tool execution failed at iteration {}", iter, e);
            notifyError(e, iter, chatId);
            return null;
        }
    }

    /**
     * 根据工具名称查找对应的 ToolCallback
     */
    private ToolCallback findCallback(String toolName, ToolCallback[] callbacks) {
        for (ToolCallback cb : callbacks) {
            if (cb.getToolDefinition() != null && toolName.equals(cb.getToolDefinition().name())) {
                return cb;
            }
        }
        return null;
    }

    /**
     * 构建 ToolContext，参照 DefaultToolCallingManager.buildToolContext() 实现。
     * <p>
     * 从 Prompt 的 ChatOptions 中提取 ToolCallingChatOptions.getToolContext()，
     * 将其封装为 ToolContext 对象传入工具调用，确保工具可以访问上下文数据。
     *
     * @param prompt 当前请求的 Prompt
     * @return ToolContext 实例（即使为空 Map 也会返回有效的 ToolContext）
     */
    private ToolContext buildToolContext(Prompt prompt) {
        Map<String, Object> toolContextMap = Map.of();
        ChatOptions options = prompt.getOptions();
        if (options instanceof ToolCallingChatOptions toolCallingChatOptions) {
            if (!CollectionUtils.isEmpty(toolCallingChatOptions.getToolContext())) {
                toolContextMap = new HashMap<>(toolCallingChatOptions.getToolContext());
            }
        }
        return new ToolContext(toolContextMap);
    }

    /**
     * 从 request 的 ChatOptions 中解析已注册的工具回调
     */
    private ToolCallback[] resolveToolCallbacks(ChatClientRequest request) {
        ChatOptions options = request.prompt().getOptions();
        if (options instanceof ToolCallingChatOptions tcOptions && tcOptions.getToolCallbacks() != null) {
            List<ToolCallback> list = tcOptions.getToolCallbacks();
            return list.toArray(new ToolCallback[0]);
        }
        return new ToolCallback[0];
    }

    // ==================== 直接模型调用 ====================

    /**
     * 直接调用 ChatModel（不经过 Advisor Chain）
     * 用于 ReAct 循环中的后续推理，避免重复触发 Memory 等 Advisor
     */
    private ChatResponse callModelDirect(List<Message> messages, ChatClientRequest request, String chatId) {
        try {
            ChatOptions options = request.prompt().getOptions();
            // 确保禁用自动工具执行
            ToolCallback[] toolArray = resolveToolCallbacks(request);
            ChatOptions directOptions = ToolCallingChatOptions.builder()
                    .internalToolExecutionEnabled(false)
                    .toolCallbacks(toolArray)
                    .build();

            Prompt prompt = new Prompt(messages, directOptions);
            return chatModel.call(prompt);
        } catch (Exception e) {
            log.error("[ReAct] Direct model call failed", e);
            notifyError(e, -1, chatId);
            return null;
        }
    }

    // ==================== Middleware 通知 ====================

    private void notifySetContext(ChatClientRequest request, String chatId) {
        for (var mw : middlewares) {
            try {
                mw.setContext(request, chatId);
            } catch (Exception e) {
                log.warn("Middleware setContext error", e);
            }
        }
    }

    private void notifyBeforeReasoning(List<Message> messages, int iter, String chatId) {
        for (var mw : middlewares) {
            try {
                mw.beforeReasoning(messages, iter, chatId);
            } catch (Exception e) {
                log.warn("Middleware error", e);
            }
        }
    }

    private void notifyAfterReasoning(ChatResponse response, int iter, String chatId) {
        for (var mw : middlewares) {
            try {
                mw.afterReasoning(response, iter, chatId);
            } catch (Exception e) {
                log.warn("Middleware error", e);
            }
        }
    }

    private void notifyBeforeActing(List<AssistantMessage.ToolCall> toolCalls, int iter, String chatId) {
        for (var mw : middlewares) {
            try {
                mw.beforeActing(toolCalls, iter, chatId);
            } catch (Exception e) {
                log.warn("Middleware error", e);
            }
        }
    }

    private void notifyAfterActing(ToolResponseMessage msg, int iter, String chatId) {
        for (var mw : middlewares) {
            try {
                mw.afterActing(msg, iter, chatId);
            } catch (Exception e) {
                log.warn("Middleware error", e);
            }
        }
    }

    private void notifyComplete(int totalIters, String finalResponse, String chatId) {
        for (var mw : middlewares) {
            try {
                mw.onComplete(totalIters, finalResponse, chatId);
            } catch (Exception e) {
                log.warn("Middleware error", e);
            }
        }
    }

    private void notifyError(Exception error, int iter, String chatId) {
        for (var mw : middlewares) {
            try {
                mw.onError(error, iter, chatId);
            } catch (Exception e) {
                log.warn("Middleware error", e);
            }
        }
    }

    // ==================== Advisor 元数据 ====================

    @Override
    public String getName() {
        return "ReActAdvisor";
    }

    @Override
    public int getOrder() {
        return this.order;
    }

    // ==================== Builder ====================

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private ChatModel chatModel;
        private List<ReActMiddleware> middlewares = new ArrayList<>();
        private int maxIterations = 10;
        private int order = 100; // 高于 ToolCallAdvisor 的默认优先级

        public Builder chatModel(ChatModel chatModel) {
            this.chatModel = chatModel;
            return this;
        }

        public Builder addMiddleware(ReActMiddleware middleware) {
            this.middlewares.add(middleware);
            return this;
        }

        public Builder middlewares(List<ReActMiddleware> middlewares) {
            this.middlewares = new ArrayList<>(middlewares);
            return this;
        }

        public Builder maxIterations(int maxIterations) {
            if (maxIterations <= 0) {
                throw new IllegalArgumentException("maxIterations must be positive, got: " + maxIterations);
            }
            this.maxIterations = maxIterations;
            return this;
        }

        public Builder order(int order) {
            this.order = order;
            return this;
        }

        public ReActAdvisor build() {
            if (chatModel == null) {
                throw new IllegalStateException("chatModel is required");
            }
            return new ReActAdvisor(this);
        }

        public Builder autoInjectMiddleware() {
            try {
                var middlewareBeans = SpringUtil.getContext().getBeansOfType(ReActMiddleware.class);
                middlewareBeans.values().forEach(this::addMiddleware);
            } catch (Exception e) {
                log.debug("No ReActMiddleware beans found", e);
            }
            return this;
        }
    }
}
