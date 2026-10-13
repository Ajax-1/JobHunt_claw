package com.git.hui.jobclaw.core.router.intent;

import com.git.hui.jobclaw.core.router.intent.classifier.IntentClassificationRes;

import java.util.Optional;

/**
 * Agent路由接口
 * 
 * 职责：
 * 1. 根据意图识别结果路由到合适的Agent
 * 2. 支持强制路由到指定Agent
 * 3. 处理Agent切换逻辑
 * 
 * @date 2026/4/17
 */
public interface AgentRouter {
    
    /**
     * 路由到Agent
     * 
     * @param classification 意图识别结果
     * @param currentBoundAgent 当前绑定的Agent（可选）
     * @return 路由结果
     */
    RouterResult route(IntentClassificationRes classification,
                       Optional<String> currentBoundAgent);
    
    /**
     * 强制路由到指定Agent
     * 
     * @param agentId Agent ID
     * @return 路由结果
     */
    RouterResult routeTo(String agentId);
    
    /**
     * 判断是否应该保持当前Agent
     * AIDEV-NOTE: 用于判断意图变化时是否切换Agent
     * 
     * @param currentAgentId 当前Agent ID
     * @param newIntentType 新意图类型
     * @return 是否应该保持
     */
    default boolean shouldKeepAgent(String currentAgentId, PresetAgentIntro newIntentType) {
        // AIDEV-NOTE: 这里可以根据Agent支持的意图类型来判断
        // 可在配置中定义哪些意图类型之间可以保持
        return false;
    }
    
    /**
     * 路由结果
     * 
     * @param agentId 目标Agent ID
     * @param isNewSession 是否是新会话（需要初始化）
     * @param reason 路由原因
     */
    record RouterResult(
            String agentId,
            boolean isNewSession,
            String reason
    ) {
        /**
         * 便捷方法：创建新会话结果
         */
        public static RouterResult newSession(String agentId, String reason) {
            return new RouterResult(agentId, true, reason);
        }
        
        /**
         * 便捷方法：创建继续会话结果
         */
        public static RouterResult continuing(String agentId, String reason) {
            return new RouterResult(agentId, false, reason);
        }
    }
}