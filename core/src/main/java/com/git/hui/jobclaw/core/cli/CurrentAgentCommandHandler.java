package com.git.hui.jobclaw.core.cli;

import com.git.hui.jobclaw.core.agent.BizAgent;
import com.git.hui.jobclaw.core.agent.models.UserConversationInfo;
import com.git.hui.jobclaw.core.channel.ChannelReceiveMessage;
import com.git.hui.jobclaw.core.router.intent.AgentRegistry;
import com.git.hui.jobclaw.core.router.intent.PresetAgentIntro;
import com.git.hui.jobclaw.core.router.intent.SessionAgentBinder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.function.Function;

/**
 * 查看当前Agent命令处理器 (/current)
 *
 * AIDEV-NOTE: 显示当前会话绑定的Agent信息，包括绑定时间、过期时间等
 *
 * @date 2026/4/17
 */
@Component
public class CurrentAgentCommandHandler implements SystemCommandHandler {

    private final SessionAgentBinder sessionBinder;
    private final AgentRegistry agentRegistry;

    public CurrentAgentCommandHandler(SessionAgentBinder sessionBinder, AgentRegistry agentRegistry) {
        this.sessionBinder = sessionBinder;
        this.agentRegistry = agentRegistry;
    }

    @Override
    public boolean handle(ChannelReceiveMessage msg, UserConversationInfo conversationInfo, String command, Function<String, Boolean> process) {
        Optional<SessionAgentBinder.BoundAgentInfo> boundAgent = sessionBinder.getBoundAgent(conversationInfo.jobClawUserId(),
                conversationInfo.conversationId());

        StringBuilder sb = new StringBuilder();
        if (boundAgent.isPresent()) {
            SessionAgentBinder.BoundAgentInfo info = boundAgent.get();
            String agentId = info.agentId();

            // 获取Agent的描述信息
            Optional<BizAgent> agentOpt = agentRegistry.getAgent(agentId);
            String description = "未知Agent";
            if (agentOpt.isPresent()) {
                var intro = agentOpt.get().getAgentIntro();
                description = intro.getDescription();
            }

            sb.append("🤖 当前会话绑定的 Agent：\n\n");
            sb.append(String.format("**%s**\n\n", agentId));
            sb.append(String.format("描述：%s\n\n", description));
            sb.append(String.format("绑定时间：%s\n", LocalDateTime.ofInstant(info.boundAt(), ZoneId.systemDefault())));
            sb.append(String.format("过期时间：%s\n", LocalDateTime.ofInstant(info.expiresAt(), ZoneId.systemDefault())));
            sb.append("\n💡 提示：使用 `/agent <名称>` 命令可以切换到其他Agent");
        } else {
            sb.append("📭 当前会话未绑定任何Agent\n\n");
            sb.append("您可以：\n");
            sb.append("- 直接告诉我您的需求，我会自动为您匹配合适的Agent\n\n");
            sb.append("- 使用 `/agents` 查看所有可用的Agent列表\n\n");
            sb.append("- 使用 `/agent <名称>` 手动切换到指定Agent");
        }

        return process.apply(sb.toString());
    }

    @Override
    public PresetAgentIntro getIntentType() {
        return PresetAgentIntro.CURRENT_AGENT;
    }

    @Override
    public String getDescription() {
        return "查看当前会话绑定的Agent";
    }
}
