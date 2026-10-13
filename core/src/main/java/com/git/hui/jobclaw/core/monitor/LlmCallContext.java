package com.git.hui.jobclaw.core.monitor;

import com.git.hui.jobclaw.core.agent.models.UserConversationInfo;

/**
 * LlmCallContext 记录类，用于封装大语言模型调用时的上下文信息
 * 该记录类包含了调用所需的各种标识符和参数
 */
public record LlmCallContext(String invocationId, String jobClawUserId, String conversationId,
                             String channel, String agent, String operation, String mode) {

    /**
     * 创建并返回一个新的 LlmCallContext 实例
     *
     * @param chatId 聊天ID，作为调用标识符
     * @param user 包含用户对话信息的对象
     * @param operation 操作类型
     * @param mode 调用模式
     * @return 返回一个新的 LlmCallContext 实例，包含传入的参数信息
     */
    public static LlmCallContext of(String chatId, UserConversationInfo user, String operation, String mode) {
        return new LlmCallContext(chatId, user.jobClawUserId(), user.genId(),
                user.channel(), user.agent(), operation, mode);
    }
}
