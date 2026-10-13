package com.git.hui.jobclaw.core.cli;

import com.git.hui.jobclaw.core.agent.models.UserConversationInfo;
import com.git.hui.jobclaw.core.channel.ChannelReceiveMessage;
import com.git.hui.jobclaw.core.router.intent.AgentRegistry;
import com.git.hui.jobclaw.core.router.intent.PresetAgentIntro;
import com.git.hui.jobclaw.core.router.intent.SessionAgentBinder;
import org.springframework.stereotype.Component;

import java.util.function.Function;

/**
 * Enters plan mode by binding the current conversation to PlanBizAgent.
 */
@Component
public class PlanModeCommandHandler implements SystemCommandHandler {

    private final SessionAgentBinder sessionBinder;
    private final AgentRegistry agentRegistry;

    public PlanModeCommandHandler(SessionAgentBinder sessionBinder, AgentRegistry agentRegistry) {
        this.sessionBinder = sessionBinder;
        this.agentRegistry = agentRegistry;
    }

    @Override
    public boolean supports(String command) {
        return PresetAgentIntro.PLAN.getCommand().equals(command.trim());
    }

    @Override
    public boolean handle(ChannelReceiveMessage msg,
                          UserConversationInfo conversationInfo,
                          String command,
                          Function<String, Boolean> process) {
        boolean available = agentRegistry.getAgent(PresetAgentIntro.PLAN.getAgentId())
                .filter(agent -> agent.isAvailable())
                .isPresent();
        if (!available) {
            return process.apply("计划模式当前不可用，请确认 PlanNotebook 插件已启用。");
        }
        sessionBinder.bind(
                conversationInfo.jobClawUserId(),
                conversationInfo.conversationId(),
                PresetAgentIntro.PLAN.getAgentId()
        );
        return process.apply("""
                已进入计划模式。

                请告诉我需要完成的复杂目标。我会先拆解计划，再逐步执行并持续更新进度。

                使用 `/reset` 可以退出计划模式。
                """);
    }

    @Override
    public PresetAgentIntro getIntentType() {
        return PresetAgentIntro.PLAN;
    }

    @Override
    public String getDescription() {
        return "进入计划模式";
    }
}
