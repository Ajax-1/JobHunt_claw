package com.git.hui.jobclaw.core.agent.memory.episodic;

import java.time.Instant;

/**
 * 情景记忆事实 — 从对话中提取的单个关键事实
 *
 * <p>每条事实包含分类、内容和时间戳，用于跨会话记忆检索。
 *
 * @param category  事实分类：preference(偏好), decision(决策), info(个人信息), todo(待办), conclusion(结论)
 * @param content   事实内容（简短描述，不超过30字）
 * @param createdAt 创建时间
 * @param sourceId  来源会话标识（用于追溯）
 *
 * <p>AIDEV-NOTE: Phase 3 — 情景记忆数据模型
 */
public record EpisodicFact(
        String category,
        String content,
        Instant createdAt,
        String sourceId
) {
    /**
     * 创建一个新的事实（自动设置时间戳）
     */
    public static EpisodicFact of(String category, String content) {
        return new EpisodicFact(category, content, Instant.now(), null);
    }

    /**
     * 创建一个带来源的事实
     */
    public static EpisodicFact of(String category, String content, String sourceId) {
        return new EpisodicFact(category, content, Instant.now(), sourceId);
    }

    /**
     * 格式化为可读文本
     */
    public String toDisplayString() {
        return "[" + category + "] " + content;
    }
}
