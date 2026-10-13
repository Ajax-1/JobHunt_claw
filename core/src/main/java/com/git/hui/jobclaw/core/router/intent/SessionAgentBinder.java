package com.git.hui.jobclaw.core.router.intent;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 会话状态管理器接口
 * 
 * 职责：
 * 1. 绑定会话到指定Agent
 * 2. 获取当前会话绑定的Agent
 * 3. 解除绑定
 * 4. 判断是否需要重新意图识别
 * 
 * AIDEV-NOTE: 会话状态需要持久化，避免重启后丢失
 * 
 * @date 2026/4/17
 */
public interface SessionAgentBinder {
    
    /**
     * 绑定会话到指定Agent
     * 
     * @param jobClawUserId 用户ID
     * @param sessionId 会话ID
     * @param agentId Agent ID
     */
    void bind(String jobClawUserId, String sessionId, String agentId);
    
    /**
     * 绑定会话到指定Agent（带过期时间）
     * 
     * @param jobClawUserId 用户ID
     * @param sessionId 会话ID
     * @param agentId Agent ID
     * @param expiresAt 过期时间
     */
    default void bind(String jobClawUserId, String sessionId, String agentId, Instant expiresAt) {
        // 默认实现忽略过期时间参数
        bind(jobClawUserId, sessionId, agentId);
    }
    
    /**
     * 获取当前会话绑定的Agent
     * 
     * @param jobClawUserId 用户ID
     * @param sessionId 会话ID
     * @return Agent ID，如果未绑定或已过期则返回空
     */
    Optional<BoundAgentInfo> getBoundAgent(String jobClawUserId, String sessionId);
    
    /**
     * 便捷方法：仅获取Agent ID
     */
    default Optional<String> getBoundAgentId(String jobClawUserId, String sessionId) {
        return getBoundAgent(jobClawUserId, sessionId).map(BoundAgentInfo::agentId);
    }
    
    /**
     * 解除绑定
     * 
     * @param jobClawUserId 用户ID
     * @param sessionId 会话ID
     */
    void unbind(String jobClawUserId, String sessionId);
    
    /**
     * 判断是否需要重新意图识别
     * 
     * 判断逻辑：
     * - 用户发送 /reset: 需要
     * - 不存在绑定: 需要
     * - 绑定已过期: 需要
     * - 用户发送了明确的Agent切换命令: 不需要（在调用方处理）
     * - 其他情况: 不需要
     * 
     * @param jobClawUserId 用户ID
     * @param sessionId 会话ID
     * @param userMessage 用户消息（用于判断是否为reset命令）
     * @return 是否需要重新意图识别
     */
    boolean needsIntentRecognition(String jobClawUserId, String sessionId, String userMessage);
    
    /**
     * 续期绑定状态
     * 
     * @param jobClawUserId 用户ID
     * @param sessionId 会话ID
     * @return 是否续期成功
     */
    default boolean renew(String jobClawUserId, String sessionId) {
        Optional<BoundAgentInfo> bound = getBoundAgent(jobClawUserId, sessionId);
        if (bound.isPresent()) {
            // 续期6小时
            bind(jobClawUserId, sessionId, bound.get().agentId(), 
                    Instant.now().plusSeconds(6 * 3600));
            return true;
        }
        return false;
    }
    
    /**
     * 获取意图历史
     * 
     * @param jobClawUserId 用户ID
     * @param sessionId 会话ID
     * @return 意图历史列表
     */
    List<IntentHistoryItem> getIntentHistory(String jobClawUserId, String sessionId);
    
    /**
     * 添加意图历史
     * 
     * @param jobClawUserId 用户ID
     * @param sessionId 会话ID
     * @param intentType 意图类型
     * @param confidence 置信度
     */
    default void addIntentHistory(String jobClawUserId, String sessionId,
                                  PresetAgentIntro intentType, double confidence) {
        // 默认空实现，子类可覆盖
    }
    
    /**
     * 绑定信息
     * 
     * @param agentId Agent ID
     * @param boundAt 绑定时间
     * @param expiresAt 过期时间
     */
    record BoundAgentInfo(
            String agentId,
            Instant boundAt,
            Instant expiresAt
    ) {}
    
    /**
     * 意图历史项
     * 
     * @param intentType 意图类型
     * @param confidence 置信度
     * @param timestamp 时间戳
     */
    record IntentHistoryItem(
            PresetAgentIntro intentType,
            double confidence,
            Instant timestamp
    ) {}
}