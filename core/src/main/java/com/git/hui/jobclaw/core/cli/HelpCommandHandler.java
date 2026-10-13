package com.git.hui.jobclaw.core.cli;

import com.git.hui.jobclaw.core.agent.models.UserConversationInfo;
import com.git.hui.jobclaw.core.channel.ChannelReceiveMessage;
import com.git.hui.jobclaw.core.router.intent.PresetAgentIntro;
import com.git.hui.jobclaw.core.utils.SpringUtil;
import org.springframework.stereotype.Component;

import java.util.function.Function;

/**
 * 帮助命令处理器 (/help)
 *
 * AIDEV-NOTE: 显示系统帮助信息，包括可用命令和功能介绍
 *
 * @date 2026/4/17
 */
@Component
public class HelpCommandHandler implements SystemCommandHandler {

    @Override
    public boolean handle(ChannelReceiveMessage msg, UserConversationInfo conversationInfo, String command, Function<String, Boolean> process) {
        String helpText = SpringUtil.getBean(SystemCommandDispatcher.class).getAllCommandDescriptions();
        return process.apply(String.format("""
                您好！我是JobClaw助手，请问有什么可以帮助您的？

                可用命令：
                                
                %s

                我可以帮您：
                - 推荐岗位 - 根据您的偏好推荐合适的岗位
                - 订阅推送 - 订阅您感兴趣的岗位推送通知
                - 查询状态 - 查看您的投递记录和面试状态
                - 收集信息 - 帮您收集和整理岗位信息
                """, helpText));
    }

    @Override
    public PresetAgentIntro getIntentType() {
        return PresetAgentIntro.HELP;
    }

    @Override
    public String getDescription() {
        return "显示帮助信息";
    }
}
