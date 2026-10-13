package com.git.hui.jobclaw.core.providers;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.math.BigDecimal;

/**
 *
 * @date 2026/4/9
 */
@Data
@Builder(toBuilder = true)
public class ModelConfig {
    /**
     * 模型的接入API风格，如厂家自定义的，或者OpenAI的接口风格
     */
    private String apiStyle;

    /**
     * 模型提供方，如 DeepSeek、硅基流动、阿里、OpenAI、Anthropic 等
     */
    private String provider;


    private String baseUrl = "https://api.openai.com";

    /**
     * 对话模型路径
     */
    private String completionsPath = "/v1/chat/completions";

    /**
     * 嵌入模型
     */
    private String embeddingsPath = "/v1/embeddings";

    /**
     * 图片生成
     */
    private String imagesPath = "v1/images/generations";

    /**
     * 语音合成
     */
    private String speechPath = "/v1/audio/speech";

    /**
     * 语音识别
     */
    private String transcriptionPath = "/v1/audio/transcriptions";

    /**
     * 模型信息
     */
    private List<ModelInfo> models;

    @Data
    @Builder(toBuilder = true)
    public static class ModelInfo {
        private String provider;
        private String apiKey;
        private String baseUrl;
        private String path;
        private String modelName;
        private ModelType type;
        private Boolean multimodal;
        @Builder.Default
        private Integer maxTokens = 32768;
        private String billingType;
        private BigDecimal inputPricePerMillionTokens;
        private BigDecimal outputPricePerMillionTokens;
    }

    /**
     * 模型类型
     */
    public enum ModelType {
        /**
         * 纯文本模型
         */
        TEXT,
        /**
         * 视觉理解模型
         */
        VISION,
        /**
         * 生图模型
         */
        IMAGE,
        /**
         * 视频模型
         */
        VIDEO,
        /**
         * 嵌入模型
         */
        EMBEDDING,
        /**
         * 语音识别类模型
         */
        ASR,
        /**
         * 语音合成类模型
         */
        TTS,
        ;
    }
}
