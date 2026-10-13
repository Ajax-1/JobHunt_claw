package com.git.hui.jobclaw.core.cli;

import cn.hutool.core.util.NumberUtil;
import com.git.hui.jobclaw.core.agent.BizAgent;
import com.git.hui.jobclaw.core.agent.models.UserConversationInfo;
import com.git.hui.jobclaw.core.apis.context.UserRoleEnum;
import com.git.hui.jobclaw.core.apis.service.IUserService;
import com.git.hui.jobclaw.core.channel.ChannelReceiveMessage;
import com.git.hui.jobclaw.core.router.intent.AgentRegistry;
import com.git.hui.jobclaw.core.router.intent.PresetAgentIntro;
import com.git.hui.jobclaw.core.router.intent.SessionAgentBinder;
import com.git.hui.jobclaw.core.utils.SpringUtil;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.function.Function;

/**
 * 列出Agent命令处理器 (/agents)
 *
 * AIDEV-NOTE: 显示所有可用的Agent列表及其描述
 *
 * @date 2026/4/17
 */
@Component
public class SwitchAgentCommandHandler implements SystemCommandHandler {
    private final SessionAgentBinder sessionBinder;
    private final AgentRegistry agentRegistry;

    public SwitchAgentCommandHandler(SessionAgentBinder sessionBinder, AgentRegistry agentRegistry) {
        this.sessionBinder = sessionBinder;
        this.agentRegistry = agentRegistry;
    }

    private Optional<String> parseAgentSwitchCommand(String message) {
        String agentId = message.trim().substring("/agent ".length()).trim();
        if (agentId.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(agentId);
    }

    @Override
    public boolean handle(ChannelReceiveMessage msg, UserConversationInfo conversationInfo, String command, Function<String, Boolean> process) {
        // 检查Agent切换命令，绑定到新的Agent，并返回
        Optional<String> targetAgentId = parseAgentSwitchCommand(msg.getMessage());
        if (targetAgentId.isPresent() && "list".equalsIgnoreCase(targetAgentId.get())) {
            return process.apply(showAgentList(conversationInfo.jobClawUserId()));
        }

        if (targetAgentId.isPresent() && agentRegistry.hasAgent(targetAgentId.get())) {
            var agent = agentRegistry.getAgent(targetAgentId.get()).get();
            // 权限管控
            if (agent.permission().enabled(getUserRole(conversationInfo.jobClawUserId()))) {
                sessionBinder.bind(conversationInfo.jobClawUserId(), conversationInfo.conversationId(), targetAgentId.get());
                var agentIntro = agent.getAgentIntro();
                String text = "已为您切换到 " + agentIntro.getAgentId() + "\n\n将为您提供以下支持:\n" + agentIntro.getDescription();
                return process.apply(text);
            }
        }

        // 无效的Agent ID，返回当前的Agent列表，让用户重新选择
        return process.apply(showAgentList(conversationInfo.jobClawUserId()));
    }

    private UserRoleEnum getUserRole(String jobClawUserId) {
        if (NumberUtil.isNumber(jobClawUserId)) {
            return SpringUtil.getBean(IUserService.class).getRole(jobClawUserId);
        } else {
            return UserRoleEnum.NORMAL;
        }
    }

    private String showAgentList(String jobClawUserId) {
        // 表示查询所有Agent
        var agents = agentRegistry.getAllAgents(jobClawUserId);
        StringBuilder sb = new StringBuilder();
        sb.append("📋 当前可用的 Agent 列表：\n\n");
        for (int i = 0; i < agents.size(); i++) {
            BizAgent agent = agents.get(i);
            var intro = agent.getAgentIntro();
            sb.append(String.format("%d. **%s**\n\n", i + 1, intro.getAgentId()));
            sb.append(String.format("   %s\n\n", intro.getIntro()));
            if (i < agents.size() - 1) {
                sb.append("\n");
            }
        }
        sb.append("\n💡 提示：使用 `/agent <名称>` 命令可以切换到指定Agent");
        return sb.toString();
    }

    @Override
    public PresetAgentIntro getIntentType() {
        return PresetAgentIntro.SWITCH_AGENT;
    }

    @Override
    public String getDescription() {
        return "Agent手动切换";
    }
}
