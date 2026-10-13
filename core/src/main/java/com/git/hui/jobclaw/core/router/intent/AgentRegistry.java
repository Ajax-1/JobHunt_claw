package com.git.hui.jobclaw.core.router.intent;

import com.git.hui.jobclaw.core.agent.BizAgent;

import java.util.List;
import java.util.Optional;

/**
 * Agent注册中心接口
 *
 * 职责：
 * 1. 注册业务Agent
 * 2. 根据ID获取Agent
 * 3. 根据意图类型获取Agent
 * 4. 获取所有Agent
 *
 * AIDEV-NOTE: 配合Spring的@PostConstruct实现自动注册
 *
 * @date 2026/4/17
 */
public interface AgentRegistry {

    /**
     * 注册业务Agent
     * AIDEV-NOTE: 如果Agent ID已存在，则覆盖
     *
     * @param agent 要注册的Agent
     */
    void register(BizAgent agent);

    /**
     * 批量注册Agent
     *
     * @param agents Agent列表
     */
    default void registerAll(List<BizAgent> agents) {
        agents.forEach(this::register);
    }

    /**
     * 根据Agent ID获取Agent
     *
     * @param agentId Agent ID
     * @return Agent，如果不存在返回空
     */
    Optional<BizAgent> getAgent(String agentId);

    /**
     * 根据意图类型获取合适的Agent列表
     * AIDEV-NOTE: 返回按优先级排序的Agent列表
     *
     * @param intentType 意图类型
     * @return 合适的Agent列表
     */
    List<BizAgent> getAgentsForIntent(PresetAgentIntro intentType);

    /**
     * 获取默认Agent
     * AIDEV-NOTE: 当没有合适的Agent时使用
     *
     * @return 默认Agent
     */
    Optional<BizAgent> getDefaultAgent();

    /**
     * 获取所有已注册的Agent
     *
     * @return Agent列表
     */
    List<BizAgent> getAllAgents(String jobClawUserId);

    /**
     * 检查Agent是否存在
     *
     * @param agentId Agent ID
     * @return 是否存在
     */
    default boolean hasAgent(String agentId) {
        return getAgent(agentId).isPresent();
    }

    /**
     * 检查Agent是否支持指定意图
     *
     * @param agentId Agent ID
     * @param intentType 意图类型
     * @return 是否支持
     */
    default boolean isAgentSuitable(String agentId, PresetAgentIntro intentType) {
        return getAgent(agentId)
                .map(agent -> agent.supportsIntent(intentType))
                .orElse(false);
    }

    /**
     * 注销Agent
     *
     * @param agentId Agent ID
     * @return 是否成功注销
     */
    boolean unregister(String agentId);
}