package com.git.hui.jobclaw.web.model.res;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Admin 全局大模型供应商配置。
 *
 * @date 2026/6/18
 */
@Data
public class AdminLlmProviderVo {
    private Map<String, ProviderConfigVo> providers;
    private DefaultModelVo defaults;

    @Data
    public static class ProviderConfigVo {
        private String provider;
        private String displayName;
        private String apiKey;
        private String apiStyle;
        private String baseUrl;
        private String completionsPath;
        private List<ModelConfigVo> models;
    }

    @Data
    public static class ModelConfigVo {
        private String name;
        private String type;
        private Boolean multimodal;
        private String billingType;
    }

    @Data
    public static class DefaultModelVo {
        private String text;
        private String vision;
    }
}
