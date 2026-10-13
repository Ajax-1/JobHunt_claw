package com.git.hui.jobclaw.web.controller.admin;

import com.git.hui.jobclaw.configs.service.GlobalEnvConfigService;
import com.git.hui.jobclaw.configs.dao.entity.GlobalEnvConfigEntity;
import com.git.hui.jobclaw.core.configuration.ConfigurationManager;
import com.git.hui.jobclaw.core.preference.AiUserPreferenceProperties;
import com.git.hui.jobclaw.core.providers.ModelConfig;
import com.git.hui.jobclaw.core.providers.ModelProviders;
import com.git.hui.jobclaw.web.model.req.AdminLlmProviderReq;
import com.git.hui.jobclaw.web.model.res.AdminLlmProviderTestVo;
import com.git.hui.jobclaw.web.model.res.AdminLlmProviderVo;
import com.git.hui.jobclaw.web.service.AdminLlmProviderTestService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 全局环境配置管理Controller
 *
 * @date 2026/4/9
 */
@Tag(name = "全局环境配置管理")
@RestController
@RequestMapping("/api/admin/env-config")
public class AdminGlobalEnvConfigController {

    private static final Pattern PROVIDER_PATTERN = Pattern.compile("^[A-Za-z0-9_-]+$");
    private static final String LLM_PROVIDER_PREFIX = "agent.ai.providers.";
    private static final String DEFAULT_PREFERENCE_PREFIX = "agent.ai.preference[0].";
    private static final String DEFAULT_USER_ID_KEY = DEFAULT_PREFERENCE_PREFIX + "user-id";
    private static final String DEFAULT_COLLECTOR_KEY = DEFAULT_PREFERENCE_PREFIX + "collector";
    private static final String DEFAULT_CHANNEL_PREFIX = DEFAULT_PREFERENCE_PREFIX + "channels[";
    private static final String DEFAULT_TEXT_MODEL_KEY = "agent.ai.preference[0].models.text";
    private static final String DEFAULT_VISION_MODEL_KEY = "agent.ai.preference[0].models.vision";
    private static final List<String> FALLBACK_DEFAULT_CHANNELS = List.of("feishu", "dingding", "wechat-clawbot");
    private static final Set<String> RESERVED_PROVIDER_KEYS = Set.of("new", "create", "edit");
    private static final Map<String, String> DEFAULT_PROVIDER_NAMES = Map.of(
            "deepseek", "DeepSeek",
            "silicon", "硅基流动",
            "step", "阶跃星辰"
    );

    @Autowired
    private GlobalEnvConfigService configService;

    @Autowired
    private ConfigurationManager configurationManager;

    @Autowired
    private AiUserPreferenceProperties aiUserPreferenceProperties;

    @Autowired
    private AdminLlmProviderTestService llmProviderTestService;

    @Autowired
    private ModelProviders modelProviders;

    @GetMapping("llm-providers")
    public AdminLlmProviderVo llmProviders() {
        AdminLlmProviderVo vo = new AdminLlmProviderVo();
        Map<String, AdminLlmProviderVo.ProviderConfigVo> providers = new LinkedHashMap<>();
        Map<String, AiUserPreferenceProperties.ProviderConfig> currentProviders = aiUserPreferenceProperties.getProviders();
        if (currentProviders != null) {
            currentProviders.forEach((provider, config) -> providers.put(provider, toVo(provider, config, true)));
        }
        vo.setProviders(providers);
        vo.setDefaults(defaultModelVo());
        return vo;
    }

    @PostMapping("llm-providers/defaults")
    public boolean saveDefaultModels(@RequestBody AdminLlmProviderVo.DefaultModelVo defaults) {
        Assert.notNull(defaults, "默认模型配置不能为空");
        validateDefaultModel(defaults.getText(), ModelConfig.ModelType.TEXT, "默认文本模型");
        validateDefaultModel(defaults.getVision(), ModelConfig.ModelType.VISION, "默认视觉模型");

        Map<String, Object> keyValues = new LinkedHashMap<>();
        putString(keyValues, DEFAULT_USER_ID_KEY, ModelProviders.DEFAULT_PREFERENCE);
        putString(keyValues, DEFAULT_COLLECTOR_KEY, defaultCollector());
        List<String> channels = defaultChannels();
        for (int i = 0; i < channels.size(); i++) {
            putString(keyValues, DEFAULT_CHANNEL_PREFIX + i + "]", channels.get(i));
        }
        putString(keyValues, DEFAULT_TEXT_MODEL_KEY, defaults.getText());
        putString(keyValues, DEFAULT_VISION_MODEL_KEY, defaults.getVision());
        configurationManager.updateProperties(keyValues);
        configurationManager.autoRefreshConfig(true);
        return true;
    }

    @PostMapping("llm-providers")
    public boolean saveLlmProvider(@RequestBody AdminLlmProviderReq req) {
        Assert.notNull(req, "请求不能为空");
        String originalProvider = normalizeOptionalProvider(req.getOriginalProvider());
        AdminLlmProviderVo.ProviderConfigVo config = req.getConfig();
        Assert.notNull(config, "供应商配置不能为空");
        String provider = resolveProviderKey(req.getProvider(), originalProvider);
        String displayName = normalizeDisplayName(config.getDisplayName());
        config.setDisplayName(displayName);
        Assert.hasText(config.getApiStyle(), "API 风格不能为空");
        Assert.notEmpty(config.getModels(), "至少配置一个模型");

        assertProviderKeyAvailable(provider, originalProvider);
        String apiKey = resolveApiKey(provider, originalProvider, config);
        Assert.hasText(apiKey, "API Key 不能为空");

        Map<String, Object> keyValues = flattenProvider(provider, config, apiKey);
        if (StringUtils.hasText(originalProvider) && !originalProvider.equals(provider)) {
            configService.deleteByConfigKeyPrefix(LLM_PROVIDER_PREFIX + originalProvider + ".");
        }
        configService.deleteByConfigKeyPrefix(LLM_PROVIDER_PREFIX + provider + ".");
        configurationManager.updateProperties(keyValues);
        configurationManager.autoRefreshConfig(true);
        return true;
    }

    @PostMapping("llm-providers/test")
    public AdminLlmProviderTestVo testLlmProvider(@RequestBody AdminLlmProviderReq req) {
        Assert.notNull(req, "请求不能为空");
        String originalProvider = normalizeOptionalProvider(req.getOriginalProvider());
        String provider = resolveTestProviderKey(req.getProvider(), originalProvider);
        AdminLlmProviderVo.ProviderConfigVo config = req.getConfig();
        Assert.notNull(config, "供应商配置不能为空");
        normalizeDisplayName(config.getDisplayName());
        Assert.hasText(config.getApiStyle(), "API 风格不能为空");
        Assert.notEmpty(config.getModels(), "至少配置一个模型");

        String apiKey = resolveApiKey(provider, originalProvider, config);
        return llmProviderTestService.test(provider, config, apiKey);
    }

    @DeleteMapping("llm-providers/{provider}")
    public boolean deleteLlmProvider(@PathVariable String provider) {
        provider = normalizeProvider(provider);
        configService.deleteByConfigKeyPrefix(LLM_PROVIDER_PREFIX + provider + ".");
        configurationManager.autoRefreshConfig(true);
        Map<String, AiUserPreferenceProperties.ProviderConfig> providers = aiUserPreferenceProperties.getProviders();
        if (providers != null) {
            providers.remove(provider);
        }
        return true;
    }

    private String normalizeProvider(String provider) {
        Assert.hasText(provider, "供应商内部 key 不能为空");
        provider = provider.trim();
        Assert.isTrue(PROVIDER_PATTERN.matcher(provider).matches(), "供应商内部 key 只允许字母、数字、下划线和中划线");
        return provider;
    }

    private String normalizeOptionalProvider(String provider) {
        if (!StringUtils.hasText(provider)) {
            return null;
        }
        return normalizeProvider(provider);
    }

    private String resolveProviderKey(String requestedProvider, String originalProvider) {
        if (StringUtils.hasText(originalProvider)) {
            return originalProvider;
        }
        if (StringUtils.hasText(requestedProvider) && PROVIDER_PATTERN.matcher(requestedProvider.trim()).matches()) {
            return normalizeProvider(requestedProvider);
        }
        return generateProviderKey();
    }

    private String resolveTestProviderKey(String requestedProvider, String originalProvider) {
        if (StringUtils.hasText(originalProvider)) {
            return originalProvider;
        }
        if (StringUtils.hasText(requestedProvider) && PROVIDER_PATTERN.matcher(requestedProvider.trim()).matches()) {
            return requestedProvider.trim();
        }
        return "provider_preview";
    }

    private String generateProviderKey() {
        Map<String, AiUserPreferenceProperties.ProviderConfig> providers = aiUserPreferenceProperties.getProviders();
        String base = "provider_" + Long.toString(System.currentTimeMillis(), 36);
        String provider = base;
        int index = 1;
        while ((providers != null && providers.containsKey(provider)) || RESERVED_PROVIDER_KEYS.contains(provider)) {
            provider = base + "_" + index++;
        }
        return provider;
    }

    private String normalizeDisplayName(String displayName) {
        Assert.hasText(displayName, "供应商不能为空");
        String trimmed = displayName.trim();
        Assert.isTrue(trimmed.length() <= 64, "供应商名称不能超过 64 个字符");
        return trimmed;
    }

    private void assertProviderKeyAvailable(String provider, String originalProvider) {
        if (provider.equals(originalProvider)) {
            return;
        }
        Map<String, AiUserPreferenceProperties.ProviderConfig> providers = aiUserPreferenceProperties.getProviders();
        Assert.isTrue(!RESERVED_PROVIDER_KEYS.contains(provider) && (providers == null || !providers.containsKey(provider)), "供应商内部 key 已存在");
    }

    private String getCurrentApiKey(String provider) {
        Map<String, AiUserPreferenceProperties.ProviderConfig> providers = aiUserPreferenceProperties.getProviders();
        if (providers == null || providers.get(provider) == null) {
            return null;
        }
        return providers.get(provider).getApiKey();
    }

    private String resolveApiKey(String provider, String originalProvider, AdminLlmProviderVo.ProviderConfigVo config) {
        String apiKey = config.getApiKey();
        if (apiKey != null && apiKey.contains("***")) {
            apiKey = getCurrentApiKey(StringUtils.hasText(originalProvider) ? originalProvider : provider);
        }
        return apiKey;
    }

    private AdminLlmProviderVo.ProviderConfigVo toVo(String provider, AiUserPreferenceProperties.ProviderConfig config, boolean maskApiKey) {
        AdminLlmProviderVo.ProviderConfigVo vo = new AdminLlmProviderVo.ProviderConfigVo();
        vo.setProvider(provider);
        vo.setDisplayName(resolveDisplayName(provider, getPersistedDisplayName(provider)));
        vo.setApiKey(maskApiKey ? mask(config.getApiKey()) : config.getApiKey());
        vo.setApiStyle(config.getApiStyle());
        vo.setBaseUrl(config.getBaseUrl());
        vo.setCompletionsPath(config.getCompletionsPath());

        List<AdminLlmProviderVo.ModelConfigVo> models = new ArrayList<>();
        if (config.getModels() != null) {
            for (AiUserPreferenceProperties.ModelDefinition model : config.getModels()) {
                if (model.getType() == null || !isAdminModelType(model.getType().name())) {
                    continue;
                }
                AdminLlmProviderVo.ModelConfigVo modelVo = new AdminLlmProviderVo.ModelConfigVo();
                modelVo.setName(model.getName());
                modelVo.setType(model.getType() == null ? null : model.getType().name());
                modelVo.setMultimodal(model.getMultimodal());
                modelVo.setBillingType(model.getBillingType());
                models.add(modelVo);
            }
        }
        vo.setModels(models);
        return vo;
    }

    private String getPersistedDisplayName(String provider) {
        String displayNameKey = LLM_PROVIDER_PREFIX + provider + ".display-name";
        return configService.findByConfigKeyPrefix(displayNameKey).stream()
                .filter(item -> displayNameKey.equals(item.getConfigKey()))
                .map(GlobalEnvConfigEntity::getConfigValue)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(null);
    }

    private String resolveDisplayName(String provider, String displayName) {
        if (StringUtils.hasText(displayName)) {
            return displayName.trim();
        }
        return DEFAULT_PROVIDER_NAMES.getOrDefault(provider, provider);
    }

    private String mask(String apiKey) {
        if (!StringUtils.hasText(apiKey)) {
            return "";
        }
        String trimmed = apiKey.trim();
        if (trimmed.length() <= 8) {
            return "***";
        }
        return trimmed.substring(0, 4) + "***" + trimmed.substring(trimmed.length() - 4);
    }

    private Map<String, Object> flattenProvider(String provider, AdminLlmProviderVo.ProviderConfigVo config, String apiKey) {
        Map<String, Object> keyValues = new LinkedHashMap<>();
        String prefix = LLM_PROVIDER_PREFIX + provider + ".";
        // AIDEV-NOTE: 扁平化给 Binder 绑定
        putString(keyValues, prefix + "display-name", config.getDisplayName());
        putString(keyValues, prefix + "api-key", apiKey);
        putString(keyValues, prefix + "api-style", config.getApiStyle());
        putString(keyValues, prefix + "base-url", config.getBaseUrl());
        putString(keyValues, prefix + "completions-path", config.getCompletionsPath());

        for (int i = 0; i < config.getModels().size(); i++) {
            AdminLlmProviderVo.ModelConfigVo model = config.getModels().get(i);
            Assert.hasText(model.getName(), "模型名称不能为空");
            Assert.hasText(model.getType(), "模型类型不能为空");
            Assert.isTrue(isAdminModelType(model.getType()), "模型类型只支持 TEXT 或 VISION");
            String modelPrefix = prefix + "models[" + i + "].";
            putString(keyValues, modelPrefix + "name", model.getName());
            putString(keyValues, modelPrefix + "type", model.getType());
            keyValues.put(modelPrefix + "multimodal", isMultimodalType(model.getType()) || Boolean.TRUE.equals(model.getMultimodal()));
            putString(keyValues, modelPrefix + "billing-type", model.getBillingType());
        }
        return keyValues;
    }

    private boolean isMultimodalType(String type) {
        return "VISION".equalsIgnoreCase(type);
    }

    private boolean isAdminModelType(String type) {
        return "TEXT".equalsIgnoreCase(type) || "VISION".equalsIgnoreCase(type);
    }

    private AdminLlmProviderVo.DefaultModelVo defaultModelVo() {
        AdminLlmProviderVo.DefaultModelVo defaults = new AdminLlmProviderVo.DefaultModelVo();
        defaults.setText(modelProviders.getDefaultModelSelection(ModelConfig.ModelType.TEXT));
        defaults.setVision(modelProviders.getDefaultModelSelection(ModelConfig.ModelType.VISION));
        return defaults;
    }

    private void validateDefaultModel(String modelSelection, ModelConfig.ModelType type, String label) {
        Assert.hasText(modelSelection, label + "不能为空");
        ModelConfig.ModelInfo modelInfo = modelProviders.getGlobalModelInfo(modelSelection);
        // AI-GENERATED AIDEV-NOTE: TEXT 多模态模型也能承担视觉任务，保持与运行时校验一致。
        boolean compatible = modelInfo.getType() == type
                || (type == ModelConfig.ModelType.VISION && Boolean.TRUE.equals(modelInfo.getMultimodal()))
                || (type == ModelConfig.ModelType.TEXT && modelInfo.getType() == ModelConfig.ModelType.VISION);
        Assert.isTrue(compatible, label + "不支持所需能力");
    }

    private String defaultCollector() {
        AiUserPreferenceProperties.UserPreferenceEntry preference = currentDefaultPreference();
        if (preference != null && preference.getCollector() != null) {
            return preference.getCollector().name();
        }
        return AiUserPreferenceProperties.CollectorType.AI_BASED.name();
    }

    private List<String> defaultChannels() {
        AiUserPreferenceProperties.UserPreferenceEntry preference = currentDefaultPreference();
        if (preference != null && preference.getChannels() != null && !preference.getChannels().isEmpty()) {
            return preference.getChannels();
        }
        return FALLBACK_DEFAULT_CHANNELS;
    }

    private AiUserPreferenceProperties.UserPreferenceEntry currentDefaultPreference() {
        List<AiUserPreferenceProperties.UserPreferenceEntry> preferences = aiUserPreferenceProperties.getPreference();
        if (preferences == null) {
            return null;
        }

        AiUserPreferenceProperties.UserPreferenceEntry partialDefault = null;
        for (AiUserPreferenceProperties.UserPreferenceEntry preference : preferences) {
            if (preference == null) {
                continue;
            }
            if (ModelProviders.DEFAULT_PREFERENCE.equals(preference.getUserId())) {
                return preference;
            }
            if (preference.getUserId() == null && preference.getModels() != null) {
                partialDefault = preference;
            }
        }
        return partialDefault;
    }

    private void putString(Map<String, Object> keyValues, String key, String value) {
        keyValues.put(key, value == null ? "" : value.trim());
    }
}
