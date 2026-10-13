package com.git.hui.jobclaw.core.preference;

import cn.hutool.core.util.NumberUtil;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.git.hui.jobclaw.core.preference.repository.AiUserPreferenceService;
import com.git.hui.jobclaw.core.providers.ModelConfig;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Lazy;

import java.util.List;
import java.util.Map;

/**
 * AI 模型配置属性
 * @date 2026/4/9
 */
@Data
@ConfigurationProperties(prefix = "agent.ai")
public class AiUserPreferenceProperties {

    /**
     * 用户级别的偏好配置（按用户ID分组）
     * 注意：由于用户ID可能包含特殊字符，使用 List 而非 Map
     */
    private List<UserPreferenceEntry> preference;

    @Autowired
    @Lazy
    private AiUserPreferenceService aiUserPreferenceService;

    /**
     * 模型提供商配置
     * key: 提供商名称 (如: deepseek, silicon)
     * value: 提供商的配置信息
     */
    private Map<String, ProviderConfig> providers;
    public UserPreferenceEntry getUserPreference(String userId) {
        if (userId == null || preference == null) {
            return null;
        }

        UserPreferenceEntry defaultFallback = null;
        for (UserPreferenceEntry entry : preference) {
            if (entry == null) {
                continue;
            }
            if (userId.equals(entry.getUserId())) {
                return entry;
            }
            if ("total".equals(userId) && entry.getUserId() == null && entry.getModels() != null) {
                defaultFallback = entry;
            }
        }
        if (defaultFallback != null) {
            return defaultFallback;
        }
        if (NumberUtil.isNumber(userId)) {
            return aiUserPreferenceService.loadUserPreferenceConfiguration(Long.valueOf(userId));
        } else {
            return null;
        }
    }

    /**
     * 用户模型配置条目
     */
    @Data
    public static class UserPreferenceEntry {
        /**
         * 用户ID
         */
        @JsonPropertyDescription("用户ID")
        private String userId;

        /**
         * 个人画像收集，采用预设问题的方案，还是基于大模型的生成问题的方案
         * 默认是：AI_BASED
         */
        @JsonPropertyDescription("用户画像收集，采用预设问题的方案，还是基于大模型的生成问题的方案")
        private CollectorType collector;

        /**
         * 用户配置的优先接受后台推送消息的通道
         */
        @JsonPropertyDescription("用户配置的优先接受后台推送消息的通道")
        private List<String> channels;

        /**
         * 用户的模型偏好配置
         */
        @JsonPropertyDescription("用户的模型偏好配置")
        private UserModelPreference models;

        /**
         * 模型提供商配置
         * key: 提供商名称 (如: deepseek, silicon)
         * value: 提供商的配置信息
         */
        @JsonPropertyDescription("模型提供商配置")
        private Map<String, ProviderConfig> providers;
    }


    /**
     * Collector type enum
     */
    public enum CollectorType {
        /** Rule-based with predefined questions */
        RULE_BASED,
        /** AI-driven dynamic conversation */
        AI_BASED
    }

    /**
     * 用户模型偏好配置
     */
    @Data
    public static class UserModelPreference {
        /**
         * 视觉模型配置，格式: provider#modelName (如: deepseek#deepseek-flash)
         */
        @JsonPropertyDescription("视觉模型配置，格式: provider#modelName (如: deepseek#deepseek-flash)")
        private String vision;

        /**
         * 文本模型配置，格式: provider#modelName (如: deepseek#deepseek-flash)
         */
        @JsonPropertyDescription("文本模型配置，格式: provider#modelName (如: deepseek#deepseek-flash)")
        private String text;

        @JsonPropertyDescription("TTS 模型配置，格式: provider#modelName (如: silicon#xxx)")
        private String tts;

        @JsonPropertyDescription("ASR 模型配置，格式: provider#modelName (如: silicon#xxx)")
        private String asr;

        @JsonPropertyDescription("图像模型配置，格式: provider#modelName (如: silicon#xxx)")
        private String image;

        @JsonPropertyDescription("嵌入模型配置，格式: provider#modelName (如: silicon#xxx)")
        private String embedding;

        @JsonPropertyDescription("视频模型配置，格式: provider#modelName (如: silicon#xxx)")
        private String video;
    }

    /**
     * 用户级别的提供商配置
     */
    @Data
    public static class UserProviderConfig {
        /**
         * API Key
         */
        @JsonPropertyDescription("API Key")
        private String apiKey;

        /**
         * 模型级别的 API Key 覆盖
         * key: 模型名称
         * value: API Key
         */
        @JsonPropertyDescription("模型级别的 API Key 覆盖")
        private List<Map<String, String>> models;
    }

    /**
     * 提供商配置
     */
    @Data
    public static class ProviderConfig {
        /**
         * API 风格 (如: openai)
         */
        private String apiStyle;

        /**
         * API Key
         */
        private String apiKey;

        /**
         * 基础 URL
         */
        private String baseUrl;

        /**
         * 对话完成路径
         */
        private String completionsPath;

        /**
         * 嵌入模型路径
         */
        private String embeddingsPath;

        /**
         * 图片生成路径
         */
        private String imagesPath;

        /**
         * 语音合成路径
         */
        private String speechPath;

        /**
         * 语音识别路径
         */
        private String transcriptionPath;

        /**
         * 模型列表
         */
        private List<ModelDefinition> models;
    }

    /**
     * 模型定义
     */
    @Data
    public static class ModelDefinition {
        /**
         * 模型名称
         */
        private String name;

        /**
         * 模型类型 (TEXT, VISION, IMAGE, VIDEO, EMBEDDING, ASR, TTS)
         */
        private ModelConfig.ModelType type;

        /**
         * 是否支持多模态
         */
        private Boolean multimodal;

        /**
         * 最大 token 数
         */
        private Integer maxTokens;
        /**
         * 计费类型: FREE / PAID
         */
        private String billingType;
        private java.math.BigDecimal inputPricePerMillionTokens;
        private java.math.BigDecimal outputPricePerMillionTokens;
    }
}
