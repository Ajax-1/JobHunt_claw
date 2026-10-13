package com.git.hui.jobclaw.core.cli;

import com.git.hui.jobclaw.core.agent.models.UserConversationInfo;
import com.git.hui.jobclaw.core.channel.ChannelReceiveMessage;
import com.git.hui.jobclaw.core.router.intent.PresetAgentIntro;

import java.util.function.Function;

/**
 * 系统命令处理器接口
 *
 * 职责：处理特定的系统命令（如 /help, /agents, /current 等）
 *
 * AIDEV-NOTE: 每个系统命令对应一个处理器实现，采用策略模式统一管理
 *
 * @date 2026/4/17
 */
public interface SystemCommandHandler {

    /**
     * 判断是否支持处理该命令
     *
     * @param command 命令字符串（已标准化为小写）
     * @return 是否支持
     */
    default boolean supports(String command) {
        return command.startsWith(getIntentType().getCommand());
    }

    /**
     * 执行命令处理
     *
     * @param msg 原始消息
     * @param conversationInfo 会话信息
     * @param command 原始命令字符串（已去除首尾空白，保留参数大小写）
     * @param process 命令处理完成回调
     * @return true 表示命中，且正确处理了回调，false 表示未命中
     */
    boolean handle(ChannelReceiveMessage msg, UserConversationInfo conversationInfo, String command, Function<String, Boolean> process);

    /**
     * 获取命令对应的意图类型
     *
     * @return 意图类型
     */
    PresetAgentIntro getIntentType();

    /**
     * 获取命令描述（用于帮助文档）
     *
     * @return 命令描述
     */
    default String getDescription() {
        return getIntentType().getDescription();
    }
}
