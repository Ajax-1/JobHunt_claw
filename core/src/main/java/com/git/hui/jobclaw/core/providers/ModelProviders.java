package com.git.hui.jobclaw.core.providers;

import com.git.hui.jobclaw.core.cache.LocalCacheManager;
import com.git.hui.jobclaw.core.configuration.event.PropertiesRefreshedEvent;
import com.git.hui.jobclaw.core.preference.AiUserPreferenceProperties;
import io.micrometer.common.util.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.model.Model;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 模型提供者
 * @date 2026/4/9
 */
@Slf4j
@Component
public class ModelProviders {
    public final static String DEFAULT_PREFERENCE = "total";
    private static final String CURRENT_MODEL_CACHE = "currentModel";
    private final LocalCacheManager cacheManager;

    public ModelConfig.ModelInfo currentModelInfo(String userId) {
        return cacheManager.get(CURRENT_MODEL_CACHE, userId);
    }

    public void clearCurrentModelInfo(String userId) {
        cacheManager.remove(CURRENT_MODEL_CACHE, userId);
    }
    /**
     * 模型缓存
     */
    public Map<ModelConfig.ModelInfo, Model> modelCache;

    private record ResolvedModel(AiUserPreferenceProperties.ProviderConfig providerConfig,
                                 ModelConfig.ModelInfo modelInfo) {
    }


    /**
     * key = 接口风格，modelProvider.apiStyle();
     */
    private Map<String, ModelProvider> providerMap;


    private final AiUserPreferenceProperties aiUserPreferenceProperties;

    @Async
    @EventListener
    public void registerUserPreferenceChangeCallback(PropertiesRefreshedEvent event) {
        // 这里用于注册用户偏好配置变更之后的回调逻辑，比如当用户重置userCacheClient缓存
        if (AiUserPreferenceProperties.class.equals(event.getPropertiesClz())) {
            modelCache.clear();
            log.info("[ModelProviders] User preference changed, clear user cache");
        }
    }


    @Autowired
    public ModelProviders(List<ModelProvider> list, AiUserPreferenceProperties aiUserPreferenceProperties,
                          LocalCacheManager cacheManager) {
        this.providerMap = list.stream().collect(Collectors.toMap(ModelProvider::apiStyle, it -> it));
        this.aiUserPreferenceProperties = aiUserPreferenceProperties;
        this.modelCache = new ConcurrentHashMap<>();
        this.cacheManager = cacheManager;
        cacheManager.getCache(CURRENT_MODEL_CACHE, Duration.ofMinutes(5), 5000);
    }

    /**
     * 获取用户偏好的模型
     * @param userId 用户ID
     * @param modelType 模型类型 (vision 或 text)
     * @return ModelInfo
     */
    public Model getModel(String userId, ModelConfig.ModelType modelType) {
        var preference = aiUserPreferenceProperties.getUserPreference(userId);
        if (preference == null) {
            log.info("[ModelProviders] User preference not found, use default preference");
            preference = aiUserPreferenceProperties.getUserPreference(DEFAULT_PREFERENCE);
        }
        if (preference == null) {
            throw new RuntimeException("未找到用户偏好配置: " + userId);
        }
        if (preference.getModels() == null && !DEFAULT_PREFERENCE.equals(userId)) {
            log.info("[ModelProviders] User model preference not configured, use default preference");
            return getModel(DEFAULT_PREFERENCE, modelType);
        }
        if (preference.getModels() == null) {
            throw new RuntimeException("未配置默认模型: " + modelType);
        }

        // 首先找到用户的偏好模型
        var preferModel = switch (modelType) {
            case TEXT -> preference.getModels().getText();
            case VISION -> preference.getModels().getVision();
            case IMAGE -> preference.getModels().getImage();
            case VIDEO -> preference.getModels().getVideo();
            case EMBEDDING -> preference.getModels().getEmbedding();
            case ASR -> preference.getModels().getAsr();
            case TTS -> preference.getModels().getTts();
        };

        if (StringUtils.isBlank(preferModel) && !DEFAULT_PREFERENCE.equals(userId)) {
            // 用户没有配置API的场景下，使用默认的全局配置替代
            log.info("[ModelProviders] User API Key not configured, use default preference");
            return getModel(DEFAULT_PREFERENCE, modelType);
        }

        ResolvedModel resolved = resolveModel(preferModel, preference.getProviders(), true);
        return buildModel(userId, resolved);
    }

    /**
     * 获取用户当前选择的模型配置，返回格式为 provider#modelName。
     */
    public String getModelSelection(String userId, ModelConfig.ModelType modelType) {
        var preference = aiUserPreferenceProperties.getUserPreference(userId);
        if (preference == null) {
            preference = aiUserPreferenceProperties.getUserPreference(DEFAULT_PREFERENCE);
        }
        if (preference == null) {
            throw new RuntimeException("未找到用户偏好配置: " + userId);
        }
        if (preference.getModels() == null && !DEFAULT_PREFERENCE.equals(userId)) {
            return getModelSelection(DEFAULT_PREFERENCE, modelType);
        }
        if (preference.getModels() == null) {
            throw new RuntimeException("未配置默认模型: " + modelType);
        }

        String preferModel = selectPreferenceModel(preference.getModels(), modelType);
        if (StringUtils.isBlank(preferModel) && !DEFAULT_PREFERENCE.equals(userId)) {
            return getModelSelection(DEFAULT_PREFERENCE, modelType);
        }
        if (StringUtils.isBlank(preferModel)) {
            throw new RuntimeException("未配置默认模型: " + modelType);
        }
        return preferModel;
    }

    public String getDefaultModelSelection(ModelConfig.ModelType modelType) {
        return getModelSelection(DEFAULT_PREFERENCE, modelType);
    }

    /**
     * 列出后台全局供应商中已配置的模型，不包含 API Key。
     */
    public List<ModelConfig.ModelInfo> listGlobalModelInfos() {
        List<ModelConfig.ModelInfo> models = new ArrayList<>();
        Map<String, AiUserPreferenceProperties.ProviderConfig> providers = aiUserPreferenceProperties.getProviders();
        if (providers == null) {
            return models;
        }
        providers.forEach((provider, providerConfig) -> {
            if (providerConfig == null || providerConfig.getModels() == null) {
                return;
            }
            for (AiUserPreferenceProperties.ModelDefinition model : providerConfig.getModels()) {
                if (model == null || model.getType() == null || StringUtils.isBlank(model.getName())) {
                    continue;
                }
                models.add(ModelConfig.ModelInfo.builder()
                        .provider(provider)
                        .modelName(model.getName())
                        .type(model.getType())
                        .multimodal(model.getMultimodal())
                        .maxTokens(model.getMaxTokens())
                        .billingType(model.getBillingType())
                        .inputPricePerMillionTokens(model.getInputPricePerMillionTokens())
                        .outputPricePerMillionTokens(model.getOutputPricePerMillionTokens())
                        .build());
            }
        });
        return models;
    }

    /**
     * 直接从全局后台供应商配置中按 provider#modelName 获取模型。
     */
    public Model getGlobalModel(String modelSelection, ModelConfig.ModelType requiredType) {
        ResolvedModel resolved = resolveModel(modelSelection, null, true);
        assertCompatibleModelType(modelSelection, resolved.modelInfo(), requiredType);
        return buildModel(DEFAULT_PREFERENCE, resolved);
    }

    /**
     * 直接解析全局后台供应商配置中的模型信息。
     */
    public ModelConfig.ModelInfo getGlobalModelInfo(String modelSelection) {
        return resolveModel(modelSelection, null, true).modelInfo();
    }

    private String selectPreferenceModel(AiUserPreferenceProperties.UserModelPreference preference,
                                         ModelConfig.ModelType modelType) {
        return switch (modelType) {
            case TEXT -> preference.getText();
            case VISION -> preference.getVision();
            case IMAGE -> preference.getImage();
            case VIDEO -> preference.getVideo();
            case EMBEDDING -> preference.getEmbedding();
            case ASR -> preference.getAsr();
            case TTS -> preference.getTts();
        };
    }

    private ResolvedModel resolveModel(String modelSelection,
                                       Map<String, AiUserPreferenceProperties.ProviderConfig> userProviders,
                                       boolean fallbackGlobalProvider) {
        if (StringUtils.isBlank(modelSelection) || !modelSelection.contains("#")) {
            throw new RuntimeException("模型配置格式错误，应为 provider#modelName");
        }
        // 解析获取 provider + modelName
        var cell = modelSelection.split("#", 2);
        if (cell.length != 2 || StringUtils.isBlank(cell[0]) || StringUtils.isBlank(cell[1])) {
            throw new RuntimeException("模型配置格式错误，应为 provider#modelName");
        }
        String provider = cell[0];
        String modelName = cell[1];

        // 根据 provider 来构建 ModelInfo 的基础配置
        var providerInfo = userProviders == null ? null : userProviders.get(provider);
        if (providerInfo == null && fallbackGlobalProvider) {
            providerInfo = aiUserPreferenceProperties.getProviders() == null ? null : aiUserPreferenceProperties.getProviders().get(provider);
        }
        if (providerInfo == null) {
            throw new RuntimeException("未找到厂商配置: " + provider);
        }
        if (providerInfo.getModels() == null) {
            throw new RuntimeException("厂商未配置模型: " + provider);
        }
        var modelInfoOpt = providerInfo.getModels().stream().filter(s -> s.getName().equals(modelName)).findFirst();
        if (modelInfoOpt.isEmpty()) {
            throw new RuntimeException("未找到模型配置: " + modelName);
        }
        var modelInfo = modelInfoOpt.get();

        ModelConfig.ModelInfo personModelInfo = ModelConfig.ModelInfo.builder()
                .provider(provider)
                .modelName(modelName)
                .apiKey(providerInfo.getApiKey())
                .baseUrl(providerInfo.getBaseUrl())
                .path(providerInfo.getCompletionsPath())
                .type(modelInfo.getType())
                .multimodal(modelInfo.getMultimodal())
                .maxTokens(modelInfo.getMaxTokens())
                .billingType(modelInfo.getBillingType())
                .inputPricePerMillionTokens(modelInfo.getInputPricePerMillionTokens())
                .outputPricePerMillionTokens(modelInfo.getOutputPricePerMillionTokens())
                .build();

        return new ResolvedModel(providerInfo, personModelInfo);
    }

    private Model buildModel(String userId, ResolvedModel resolved) {
        var providerInfo = resolved.providerConfig();
        var personModelInfo = resolved.modelInfo();

        // 在真正发起网络请求前失败，前端才能提示管理员补齐供应商配置。
        if (StringUtils.isBlank(providerInfo.getApiKey())) {
            throw new RuntimeException("AI 服务商未配置 API Key: " + personModelInfo.getProvider());
        }

        cacheManager.put(CURRENT_MODEL_CACHE, userId, personModelInfo);

        // 检查缓存
        if (modelCache.containsKey(personModelInfo)) {
            return modelCache.get(personModelInfo);
        }

        // 创建新模型实例
        var modelProvider = providerMap.get(providerInfo.getApiStyle());
        if (modelProvider == null) {
            throw new RuntimeException("未找到模型提供者: " + providerInfo.getApiStyle());
        }

        var model = modelProvider.model(personModelInfo);
        modelCache.put(personModelInfo, model);
        return model;
    }

    private void assertCompatibleModelType(String modelSelection,
                                           ModelConfig.ModelInfo modelInfo,
                                           ModelConfig.ModelType requiredType) {
        ModelConfig.ModelType actualType = modelInfo.getType();
        // AIDEV-NOTE: 多模态模型声明为 TEXT 时同样可以满足 VISION 需求（如 DeepSeek V4.1 Flash）
        boolean compatible = switch (requiredType) {
            case TEXT -> actualType == ModelConfig.ModelType.TEXT || actualType == ModelConfig.ModelType.VISION;
            case VISION -> actualType == ModelConfig.ModelType.VISION || Boolean.TRUE.equals(modelInfo.getMultimodal());
            default -> actualType == requiredType;
        };
        if (!compatible) {
            throw new RuntimeException("模型类型不匹配: " + modelSelection + " 需要 " + requiredType + "，实际为 " + actualType);
        }
    }
}
