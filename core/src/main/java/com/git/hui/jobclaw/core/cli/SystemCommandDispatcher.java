package com.git.hui.jobclaw.core.cli;

import com.git.hui.jobclaw.core.agent.models.UserConversationInfo;
import com.git.hui.jobclaw.core.channel.ChannelReceiveMessage;
import com.git.hui.jobclaw.core.router.intent.PresetAgentIntro;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.function.Function;

/**
 * 系统命令调度器
 *
 * 职责：
 * 1. 统一管理所有系统命令处理器
 * 2. 根据命令字符串路由到对应的处理器
 * 3. 提供命令识别和意图分类功能
 *
 * AIDEV-NOTE: 采用策略模式，新增命令只需添加新的Handler实现即可
 *
 * @date 2026/4/17
 */
@Slf4j
@Component
public class SystemCommandDispatcher {

    private final List<SystemCommandHandler> handlers;

    public SystemCommandDispatcher(List<SystemCommandHandler> handlers) {
        this.handlers = handlers;
        log.info("系统命令处理器已注册，共 {} 个", handlers.size());
    }

    /**
     * 判断是否为系统命令
     *
     * @param message 用户消息
     * @return 是否为系统命令
     */
    public boolean isSystemCommand(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String normalized = message.trim().toLowerCase();
        return normalized.startsWith("/");
    }

    /**
     * 识别命令对应的意图类型
     *
     * @param message 用户消息
     * @return 意图类型，如果不是系统命令则返回空
     */
    public Optional<PresetAgentIntro> recognizeIntent(String message) {
        if (!isSystemCommand(message)) {
            return Optional.empty();
        }

        String normalized = message.trim().toLowerCase();
        for (SystemCommandHandler handler : handlers) {
            if (handler.supports(normalized)) {
                return Optional.of(handler.getIntentType());
            }
        }

        return Optional.empty();
    }

    /**
     * 执行系统命令
     *
     * @param msg 原始消息
     * @param conversationInfo 会话信息
     * @param message 用户消息
     * @return 响应文本，如果无法处理则返回空
     */
    public boolean executeCommand(ChannelReceiveMessage msg, UserConversationInfo conversationInfo, String message, Function<String, Boolean> process) {
        if (!isSystemCommand(message)) {
            return false;
        }

        String command = message.trim();
        String normalized = command.toLowerCase();
        for (SystemCommandHandler handler : handlers) {
            if (handler.supports(normalized)) {
                log.debug("执行系统命令: {}, 处理器: {}", normalized, handler.getClass().getSimpleName());
                try {
                    conversationInfo.setAgent("CLI-" + handler.getIntentType().getAgentId());
                    return handler.handle(msg, conversationInfo, command, process);
                } catch (Exception e) {
                    log.error("执行系统命令失败: {}", normalized, e);
                    return false;
                }
            }
        }

        log.warn("未找到支持的命令处理器: {}", normalized);
        return false;
    }

    /**
     * 获取所有已注册的命令描述（用于帮助文档）
     *
     * @return 命令描述列表
     */
    public String getAllCommandDescriptions() {
        var list = handlers.stream()
                .map(handler -> String.format("%s - %s",
                        handler.getIntentType().getCommand(),
                        handler.getDescription()))
                .toList();
        StringJoiner joiner = new StringJoiner("\n\n");
        for (String commandDescription : list) {
            joiner.add(commandDescription);
        }
        return joiner.toString();
    }
}
