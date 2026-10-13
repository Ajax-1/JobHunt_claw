package com.git.hui.jobclaw.core.agent.react;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatResponse;

import java.util.List;

/**
 * ReAct 推理循环的中间件接口
 * <p>
 * 在 ReAct 循环的各个阶段提供拦截点，用于：
 * - 日志记录 / 审计追踪
 * - 工具调用审批 / 权限控制
 * - 推理过程监控 / 限流
 * - 工具执行结果后处理
 * - 记忆注入 / 提取（Phase 3）
 * <p>
 * 所有方法均有默认空实现，按需覆写即可。
 *
 * @date 2026/6/5
 */
public interface ReActMiddleware {

    /**
     * 在 ReAct 循环开始前设置请求上下文。
     * <p>
     * 由 ReActAdvisor 在 runReactLoop 之前调用，
     * 中间件可在此提取 userId、conversationId 等信息并缓存到内部字段，
     * 供后续 beforeReasoning / afterActing 等钩子使用。
     * <p>
     * AIDEV-NOTE: Phase 3 新增 — 为 MemoryReActMiddleware 提供请求上下文
     *
     * @param request          当前请求（包含 prompt、options、context 等）
     * @param chatId           当前调用的唯一标识（由 ReActAdvisor 生成，用于跨钩子传递会话上下文）
     */
    default void setContext(ChatClientRequest request, String chatId) {
    }

    /**
     * Reasoning 阶段前：LLM 即将进行推理
     *
     * @param messages 当前对话消息列表
     * @param iter     当前迭代次数（从0开始）
     * @param chatId   当前调用的唯一标识
     */
    default void beforeReasoning(List<Message> messages, int iter, String chatId) {
    }

    /**
     * Reasoning 阶段后：LLM 完成一次推理
     *
     * @param assistantMessage LLM 的推理结果（可能包含文本和/或工具调用）
     * @param iter             当前迭代次数
     * @param chatId           当前调用的唯一标识
     */
    default void afterReasoning(AssistantMessage assistantMessage, int iter, String chatId) {
    }

    /**
     * Reasoning 阶段后：LLM 完成一次推理（带完整 ChatResponse）
     * <p>
     * 比 {@link #afterReasoning(AssistantMessage, int, String)} 多携带 ChatResponse 元数据（token 用量等），
     * 供 MonitorMiddleware 等需要计费/指标记录的中间件使用。
     * <p>
     * 默认实现委托给 {@link #afterReasoning(AssistantMessage, int, String)}，保持向前兼容。
     *
     * @param response LLM 的完整响应（包含 token 用量等元数据）
     * @param iter     当前迭代次数
     * @param chatId   当前调用的唯一标识
     */
    default void afterReasoning(ChatResponse response, int iter, String chatId) {
        if (response != null && response.getResult() != null) {
            afterReasoning(response.getResult().getOutput(), iter, chatId);
        }
    }

    /**
     * Acting 阶段前：即将执行工具
     *
     * @param toolCalls LLM 请求的工具调用列表
     * @param iter      当前迭代次数
     * @param chatId    当前调用的唯一标识
     */
    default void beforeActing(List<AssistantMessage.ToolCall> toolCalls, int iter, String chatId) {
    }

    /**
     * Acting 阶段后：工具执行完毕
     *
     * @param toolResponses 工具执行结果消息
     * @param iter          当前迭代次数
     * @param chatId        当前调用的唯一标识
     */
    default void afterActing(ToolResponseMessage toolResponses, int iter, String chatId) {
    }

    /**
     * 循环结束时：ReAct 推理循环完成
     *
     * @param totalIters    总迭代次数
     * @param finalResponse 最终回答内容
     * @param chatId        当前调用的唯一标识
     */
    default void onComplete(int totalIters, String finalResponse, String chatId) {
    }

    /**
     * 异常时：循环中发生错误
     *
     * @param error     异常
     * @param iter      发生错误时的迭代次数
     * @param chatId    当前调用的唯一标识
     */
    default void onError(Exception error, int iter, String chatId) {
    }
}
