package com.git.hui.jobclaw.core.router.intent.impl;

import cn.hutool.core.util.NumberUtil;
import com.git.hui.jobclaw.core.agent.BizAgent;
import com.git.hui.jobclaw.core.apis.context.UserRoleEnum;
import com.git.hui.jobclaw.core.apis.service.IUserService;
import com.git.hui.jobclaw.core.router.intent.AgentRegistry;
import com.git.hui.jobclaw.core.router.intent.PresetAgentIntro;
import com.git.hui.jobclaw.core.utils.SpringUtil;
import lombok.extern.slf4j.Slf4j;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 默认Agent注册中心实现
 *
 * AIDEV-NOTE: 意图类型到Agent的默认映射
 * 支持配置化扩展
 *
 * @date 2026/4/17
 */
@Slf4j
public class DefaultAgentRegistry implements AgentRegistry {

    private final Map<String, BizAgent> agents = new ConcurrentHashMap<>();
    private BizAgent defaultAgent;

    // 意图类型 -> Agent ID列表（按优先级）
    // AIDEV-NOTE: 可配置化
    private static final Map<PresetAgentIntro, List<String>> INTENT_AGENT_MAPPING = Map.of(
            PresetAgentIntro.COLLECT, List.of(PresetAgentIntro.COLLECT.getAgentId(), PresetAgentIntro.DEFAULT.getAgentId()),
            PresetAgentIntro.RECOMMEND, List.of(PresetAgentIntro.RECOMMEND.getAgentId(), PresetAgentIntro.DEFAULT.getAgentId()),
            PresetAgentIntro.SUBSCRIBE, List.of(PresetAgentIntro.SUBSCRIBE.getAgentId(), PresetAgentIntro.DEFAULT.getAgentId()),
            PresetAgentIntro.QUERY, List.of(PresetAgentIntro.QUERY.getAgentId(), PresetAgentIntro.DEFAULT.getAgentId()),
            PresetAgentIntro.PROFILE, List.of(PresetAgentIntro.PROFILE.getAgentId(), PresetAgentIntro.DEFAULT.getAgentId()),
            PresetAgentIntro.HELP, List.of(PresetAgentIntro.HELP.getAgentId(), PresetAgentIntro.DEFAULT.getAgentId()),
            PresetAgentIntro.DEFAULT, List.of(PresetAgentIntro.DEFAULT.getAgentId()),
            PresetAgentIntro.SWITCH_AGENT, List.of(),
            PresetAgentIntro.LIST_AGENTS, List.of(),
            PresetAgentIntro.RESET, List.of()
    );

    @Override
    public void register(BizAgent agent) {
        if (agent == null || agent.getAgentIntro() == null) {
            log.warn("尝试注册无效Agent: {}", agent);
            return;
        }

        agents.put(agent.getAgentIntro().getAgentId(), agent);

        // 如果是默认Agent
        if (PresetAgentIntro.DEFAULT.equals(agent.getAgentIntro())) {
            defaultAgent = agent;
        }

        log.info("注册Agent: {}", agent.getAgentIntro());
    }

    @Override
    public Optional<BizAgent> getAgent(String agentId) {
        if (agentId == null) {
            return Optional.empty();
        }
        agentId = agentId.trim();
        var agent = agents.get(agentId);
        if (agent != null) {
            return Optional.of(agent);
        }
        // 忽略大小写，看是否能找到对应的Agent
        for (Map.Entry<String, BizAgent> entry : agents.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(agentId)) {
                return Optional.of(entry.getValue());
            }
        }
        return Optional.empty();
    }

    @Override
    public List<BizAgent> getAgentsForIntent(PresetAgentIntro intentType) {
        if (intentType == null) {
            return List.of();
        }

        List<String> agentIds = INTENT_AGENT_MAPPING.get(intentType);
        if (agentIds == null || agentIds.isEmpty()) {
            // 尝试查找支持该意图的所有Agent
            return agents.values().stream()
                    .filter(agent -> agent.supportsIntent(intentType))
                    .sorted(Comparator.comparingInt(BizAgent::getPriority).reversed())
                    .collect(Collectors.toList());
        }

        return agentIds.stream()
                .map(agents::get)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingInt(BizAgent::getPriority).reversed())
                .collect(Collectors.toList());
    }

    @Override
    public Optional<BizAgent> getDefaultAgent() {
        if (defaultAgent != null) {
            return Optional.of(defaultAgent);
        }

        // 查找任意一个可用Agent作为默认
        return agents.values().stream()
                .filter(BizAgent::isAvailable)
                .findFirst();
    }

    @Override
    public List<BizAgent> getAllAgents(String jobClawUserId) {
        UserRoleEnum role;
        if (!NumberUtil.isNumber(jobClawUserId)) {
            role = UserRoleEnum.NORMAL;
        } else {
            role = SpringUtil.getBean(IUserService.class).getRole(jobClawUserId);
        }
        return agents.values().stream()
                .filter(agent -> agent.permission().enabled(role))
                .collect(Collectors.toList());
    }

    @Override
    public boolean unregister(String agentId) {
        if (agentId == null) {
            return false;
        }

        BizAgent removed = agents.remove(agentId);
        if (removed != null) {
            log.info("注销Agent: {}", agentId);
            try {
                removed.destroy();
            } catch (Exception e) {
                log.error("销毁Agent失败: {}", agentId, e);
            }
            return true;
        }
        return false;
    }
}
