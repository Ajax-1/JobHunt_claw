package com.git.hui.jobclaw.web.model.res;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 用户偏好配置
 * @date 2026/4/23
 */
@Data
public class AiUserPreferenceVo {

    private String collector;
    private List<String> channels;
    private UserPreferenceModelVo models;
    // key = 模型提供方，如 deepseek   value 是模型相关配置
    private Map<String, UserProviderConfigVo> providers;

    @Data
    public static class UserPreferenceModelVo {
        /**
         * 模型偏好配置，格式形如： deepseek#deepseek-flash 前面是模型提供方，# 后面是模型名称
         */
        private String vision;
        private String text;
        private String image;
        private String video;
        private String embedding;
        private String asr;
        private String tts;
    }

    @Data
    public static class UserProviderConfigVo {
        private String provider;
        private String apiKey;
        private String apiStyle;
        private String baseUrl;
        private String completionsPath;
        private String embeddingsPath;
        private String imagesPath;
        private String speechPath;
        private String transcriptionPath;
        private List<ModelConfigVo> models;
    }

    @Data
    public static class ModelConfigVo {
        private String name;
        private String type;
        private boolean multimodal;
    }
}
