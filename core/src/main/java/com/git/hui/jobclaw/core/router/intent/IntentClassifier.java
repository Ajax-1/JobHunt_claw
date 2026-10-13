package com.git.hui.jobclaw.core.router.intent;

import com.git.hui.jobclaw.core.agent.models.UserConversationInfo;
import com.git.hui.jobclaw.core.router.intent.classifier.IntentClassificationRes;

import java.util.List;

/**
 * 意图分类器接口
 *
 * 职责：分析用户消息，识别意图类型
 *
 * AIDEV-NOTE: 实现类应该支持组合模式，支持多个分类器级联
 *
 * @date 2026/4/17
 */
public interface IntentClassifier {

    /**
     * 识别用户意图
     *
     * @param message 用户消息
     * @param conversationHistory 对话历史（用于上下文理解，可为空）
     * @return 识别结果
     */
    IntentClassificationRes classify(UserConversationInfo userConversationInfo, String message, List<String> conversationHistory);
}