package com.git.hui.jobclaw.core.router.intent.classifier;

import com.git.hui.jobclaw.core.agent.models.UserConversationInfo;
import com.git.hui.jobclaw.core.cli.SystemCommandDispatcher;
import com.git.hui.jobclaw.core.router.intent.IntentClassifier;
import com.git.hui.jobclaw.core.router.intent.PresetAgentIntro;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * 组合意图分类器
 *
 * 实现三层识别策略：
 * 1. 命令匹配（最高优先级）
 * 2. 关键词匹配（高优先级）
 * 3. LLM识别（中优先级，作为兜底）
 *
 * AIDEV-NOTE: 这是一个组合器，组合多个分类器的结果
 *
 * @date 2026/4/17
 */
@Slf4j
@Component
public class CompositeIntentClassifier implements IntentClassifier {

    private final KeywordIntentClassifier keywordClassifier;
    private final LLMIntentClassifier llmClassifier;
    private final SystemCommandDispatcher commandDispatcher;

    // 置信度门槛
    private static final double HIGH_CONFIDENCE_THRESHOLD = 0.9;
    private static final double MEDIUM_CONFIDENCE_THRESHOLD = 0.7;

    public CompositeIntentClassifier(
            KeywordIntentClassifier keywordClassifier,
            LLMIntentClassifier llmClassifier,
            SystemCommandDispatcher commandDispatcher) {
        this.keywordClassifier = keywordClassifier;
        this.llmClassifier = llmClassifier;
        this.commandDispatcher = commandDispatcher;
    }

    @Override
    public IntentClassificationRes classify(UserConversationInfo userConversationInfo, String message, List<String> conversationHistory) {
        if (message == null || message.isBlank()) {
            return IntentClassificationRes.unknown("空消息");
        }

        // L0: 系统命令匹配（最高优先级）- 使用统一的命令调度器
        Optional<PresetAgentIntro> commandIntent = commandDispatcher.recognizeIntent(message);
        if (commandIntent.isPresent()) {
            log.debug("L0命令匹配: {}", commandIntent.get());
            return IntentClassificationRes.highConfidence(commandIntent.get(), "命令匹配");
        }

        // L1: 关键词匹配
        IntentClassificationRes keywordResult = keywordClassifier.classify(userConversationInfo, message, conversationHistory);
        if (keywordResult.isHighlyConfident()) {
            log.debug("L1关键词匹配(高置信): {}", keywordResult.intentType());
            return keywordResult;
        }

        // 如果关键词匹配结果可信度尚可，尝试增强
        if (keywordResult.isConfident()) {
            // L2: LLM验证（可选）
            // AIDEV-NOTE: 为了节省成本和响应时间，这里直接使用关键词结果
            log.debug("L1关键词匹配(中置信): {}", keywordResult.intentType());
            return keywordResult;
        }

        // L1结果不可信，降级到LLM
        if (keywordResult.confidence() < MEDIUM_CONFIDENCE_THRESHOLD) {
            log.debug("L1未匹配，降级到LLM");
            IntentClassificationRes llmResult = llmClassifier.classify(userConversationInfo, message, conversationHistory);
            if (llmResult.isConfident()) {
                log.debug("LLM匹配: {}", llmResult.intentType());
                return llmResult;
            }
        }

        // 兜底：返回Unknown
        return IntentClassificationRes.unknown("所有策略均未识别");
    }

}