package com.git.hui.jobclaw.core.cli;

import com.git.hui.jobclaw.core.agent.models.UserConversationInfo;
import com.git.hui.jobclaw.core.channel.ChannelReceiveMessage;
import com.git.hui.jobclaw.core.router.intent.PresetAgentIntro;
import com.git.hui.jobclaw.core.router.intent.SessionAgentBinder;
import org.springframework.stereotype.Component;

import java.util.function.Function;

/**
 * 重置会话命令处理器 (/reset)
 *
 * AIDEV-NOTE: 解除当前会话的Agent绑定，重置会话状态
 *
 * @date 2026/4/17
 */
@Component
public class ResetCommandHandler implements SystemCommandHandler {

    private final SessionAgentBinder sessionBinder;

    public ResetCommandHandler(SessionAgentBinder sessionBinder) {
        this.sessionBinder = sessionBinder;
    }


    @Override
    public boolean handle(ChannelReceiveMessage msg, UserConversationInfo conversationInfo, String command, Function<String, Boolean> process) {
        sessionBinder.unbind(conversationInfo.jobClawUserId(), conversationInfo.conversationId());
        return process.apply("会话状态已重置，请告诉我您想要做什么？");
    }

    @Override
    public PresetAgentIntro getIntentType() {
        return PresetAgentIntro.RESET;
    }

    @Override
    public String getDescription() {
        return "重置会话状态";
    }
}
