package com.git.hui.jobclaw.core.router.intent.impl;

import com.git.hui.jobclaw.core.agent.BizAgent;
import com.git.hui.jobclaw.core.router.intent.AgentRegistry;
import com.git.hui.jobclaw.core.router.intent.AgentRouter;
import com.git.hui.jobclaw.core.router.intent.PresetAgentIntro;
import com.git.hui.jobclaw.core.router.intent.SessionAgentBinder;
import com.git.hui.jobclaw.core.router.intent.classifier.IntentClassificationRes;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * 基于意图的Agent路由实现
 *
 * 路由逻辑：
 * 1. 如果存在绑定Agent且支持新意图，保持
 * 2. 否则根据意图类型路由到合适的Agent
 * 3. 兜底使用默认Agent
 *
 * @date 2026/4/17
 */
@Slf4j
@Component
public class IntentBasedAgentRouter implements AgentRouter {

    private final AgentRegistry agentRegistry;
    private final SessionAgentBinder sessionBinder;

    public IntentBasedAgentRouter(AgentRegistry agentRegistry, SessionAgentBinder sessionBinder) {
        this.agentRegistry = agentRegistry;
        this.sessionBinder = sessionBinder;
    }

    @Override
    public RouterResult route(IntentClassificationRes classification, Optional<String> currentBoundAgent) {
        PresetAgentIntro intentType = classification.intentType();

        // 1. 系统意图特殊处理
        if (intentType == PresetAgentIntro.HELP) {
            // HELP不路由到具体Agent，在MsgRouter中处理
            return new RouterResult(intentType.getAgentId(), true, "系统意图: HELP");
        }
        if (intentType == PresetAgentIntro.LIST_AGENTS) {
            // LIST_AGENTS不路由到具体Agent，在MsgRouter中处理
            return new RouterResult(intentType.getAgentId(), true, "系统意图: LIST_AGENTS");
        }
        if (intentType == PresetAgentIntro.RESET) {
            // RESET不路由到具体Agent，在MsgRouter中处理
            return new RouterResult(intentType.getAgentId(), false, "系统意图: RESET");
        }
        if (intentType == PresetAgentIntro.SWITCH_AGENT) {
            // SWITCH_AGENT不路由，在MsgRouter中处理
            return new RouterResult(intentType.getAgentId(), false, "系统意图: SWITCH_AGENT");
        }

        // 2. 检查是否应该保持当前Agent
        if (currentBoundAgent.isPresent()) {
            String boundAgentId = currentBoundAgent.get();

            // 检查绑定Agent是否仍然合适
            if (shouldKeepAgent(boundAgentId, intentType)) {
                log.debug("保持当前Agent: {} -> {}", boundAgentId, intentType);
                return RouterResult.continuing(boundAgentId, "保持绑定Agent: " + boundAgentId);
            }

            // 检查是否支持新意图
            if (agentRegistry.isAgentSuitable(boundAgentId, intentType)) {
                log.debug("Agent {} 支持意图 {}，继续使用", boundAgentId, intentType);
                return RouterResult.continuing(boundAgentId, "Agent支持新意图: " + intentType);
            }
        }

        // 3. 根据意图路由到Agent
        List<BizAgent> suitableAgents = agentRegistry.getAgentsForIntent(intentType);

        if (!suitableAgents.isEmpty()) {
            BizAgent selectedAgent = suitableAgents.get(0);
            boolean isNew = currentBoundAgent.isEmpty() ||
                    !currentBoundAgent.get().equals(selectedAgent.getAgentIntro().getAgentId());

            return new RouterResult(
                    selectedAgent.getAgentIntro().getAgentId(),
                    isNew,
                    "根据意图 " + intentType + " 路由到 " + selectedAgent.getAgentIntro());
        }

        // 4. 兜底到默认Agent
        Optional<BizAgent> defaultAgent = agentRegistry.getDefaultAgent();
        if (defaultAgent.isPresent()) {
            return new RouterResult(
                    defaultAgent.get().getAgentIntro().getAgentId(),
                    false,
                    "未找到合适Agent，使用默认");
        }

        // 5. 最兜底：创建__unknown__标记，让MsgRouter处理
        return new RouterResult("__unknown__", false, "无可用Agent");
    }

    @Override
    public RouterResult routeTo(String agentId) {
        boolean exists = agentRegistry.hasAgent(agentId);

        if (exists) {
            return new RouterResult(agentId, true, "强制路由到: " + agentId);
        }

        // Agent不存在，尝试获取默认
        return agentRegistry.getDefaultAgent()
                .map(agent -> new RouterResult(agent.getAgentIntro().getAgentId(), true,
                        "Agent " + agentId + " 不存在，使用默认"))
                .orElse(new RouterResult("__unknown__", true,
                        "Agent不存在且无默认"));
    }

    @Override
    public boolean shouldKeepAgent(String currentAgentId, PresetAgentIntro newIntentType) {
        // AIDEV-NOTE: 这里可以实现更复杂的保持逻辑
        // 例如：某些意图类型之间可以保持，某些不行

        // 不保持系统意图
        if (newIntentType == PresetAgentIntro.HELP ||
                newIntentType == PresetAgentIntro.LIST_AGENTS ||
                newIntentType == PresetAgentIntro.RESET ||
                newIntentType == PresetAgentIntro.SWITCH_AGENT) {
            return false;
        }

        // 检查Agent支持新意图
        return agentRegistry.isAgentSuitable(currentAgentId, newIntentType);
    }
}