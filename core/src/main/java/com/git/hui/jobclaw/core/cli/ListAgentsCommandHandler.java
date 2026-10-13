package com.git.hui.jobclaw.core.cli;

import com.git.hui.jobclaw.core.agent.BizAgent;
import com.git.hui.jobclaw.core.agent.models.UserConversationInfo;
import com.git.hui.jobclaw.core.channel.ChannelReceiveMessage;
import com.git.hui.jobclaw.core.router.intent.AgentRegistry;
import com.git.hui.jobclaw.core.router.intent.PresetAgentIntro;
import org.springframework.stereotype.Component;

import java.util.function.Function;

/**
 * 列出Agent命令处理器 (/agents)
 *
 * AIDEV-NOTE: 显示所有可用的Agent列表及其描述
 *
 * @date 2026/4/17
 */
@Component
public class ListAgentsCommandHandler implements SystemCommandHandler {

    private final AgentRegistry agentRegistry;

    public ListAgentsCommandHandler(AgentRegistry agentRegistry) {
        this.agentRegistry = agentRegistry;
    }

    @Override
    public boolean handle(ChannelReceiveMessage msg, UserConversationInfo conversationInfo, String command, Function<String, Boolean> process) {
        var agents = agentRegistry.getAllAgents(conversationInfo.jobClawUserId());

        StringBuilder sb = new StringBuilder();
        sb.append("📋 当前可用的 Agent 列表：\n\n");

        if (agents.isEmpty()) {
            sb.append("暂无可用的Agent。");
        } else {
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
        }

        return process.apply(sb.toString());
    }

    @Override
    public PresetAgentIntro getIntentType() {
        return PresetAgentIntro.LIST_AGENTS;
    }

    @Override
    public String getDescription() {
        return "列出所有可用的Agent";
    }
}
