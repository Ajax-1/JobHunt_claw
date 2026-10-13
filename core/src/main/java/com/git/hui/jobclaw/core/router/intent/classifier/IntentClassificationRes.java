package com.git.hui.jobclaw.core.router.intent.classifier;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.git.hui.jobclaw.core.router.intent.PresetAgentIntro;

import java.util.Collections;
import java.util.Map;

/**
 * 意图识别结果
 * AIDEV-NOTE: 记录意图识别的核心结果数据
 *
 * @date 2026/4/17
 */
public record IntentClassificationRes(
        /**
         * 识别到的意图类型
         */
        @JsonPropertyDescription("识别到的意图类型，大模型的返回不应该出现 [HELP, LIST_AGENTS, SWITCH_AGENT, REST] 这几个中的任何一个")
        PresetAgentIntro intentType,

        /**
         * 置信度 0.0-1.0
         * - 0.9+: 高可信，直接使用
         * - 0.7-0.9: 中可信，可以尝试
         * - 0.5-0.7: 低可信，考虑降级到其他策略
         * - <0.5: 不可信，标记为UNKNOWN
         */
        @JsonPropertyDescription("""
                 置信度 0.0-1.0
                 - 0.9+: 高可信，直接使用
                 - 0.7-0.9: 中可信，可以尝试
                 - 0.5-0.7: 低可信，考虑降级到其他策略
                 - <0.5: 不可信，标记
                """)
        double confidence,

        /**
         * 识别理由/推理过程
         */
        @JsonPropertyDescription("识别理由/推理过程")
        String reasoning,

        /**
         * 额外上下文信息
         */
        @JsonIgnore
        Map<String, Object> context
) {
    /**
     * 便捷构造函数
     */
    public IntentClassificationRes(PresetAgentIntro intentType, double confidence, String reasoning) {
        this(intentType, confidence, reasoning, Collections.emptyMap());
    }

    /**
     * 判断是否可信
     * AIDEV-NOTE: 默认置信度门槛0.7
     */
    public boolean isConfident() {
        return confidence >= 0.7;
    }

    /**
     * 判断是否高可信
     */
    public boolean isHighlyConfident() {
        return confidence >= 0.9;
    }

    /**
     * 便捷方法：创建高可信结果
     */
    public static IntentClassificationRes highConfidence(PresetAgentIntro type, String reasoning) {
        return new IntentClassificationRes(type, 1.0, reasoning);
    }

    /**
     * 便捷方法：创建低可信结果
     */
    public static IntentClassificationRes lowConfidence(PresetAgentIntro type, String reasoning) {
        return new IntentClassificationRes(type, 0.5, reasoning);
    }

    /**
     * 便捷方法：创建未知结果
     */
    public static IntentClassificationRes unknown(String reasoning) {
        return new IntentClassificationRes(PresetAgentIntro.UNKNOWN, 0.0, reasoning);
    }
}