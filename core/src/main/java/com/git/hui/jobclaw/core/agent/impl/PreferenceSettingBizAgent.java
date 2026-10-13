package com.git.hui.jobclaw.core.agent.impl;

import cn.hutool.core.util.NumberUtil;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.git.hui.jobclaw.core.agent.IIdentityAgent;
import com.git.hui.jobclaw.core.agent.models.LlmRspCell;
import com.git.hui.jobclaw.core.agent.models.UserConversationInfo;
import com.git.hui.jobclaw.core.apis.permission.AgentPermission;
import com.git.hui.jobclaw.core.channel.ChannelBinder;
import com.git.hui.jobclaw.core.channel.ChannelReceiveMessage;
import com.git.hui.jobclaw.core.configuration.ConfigurationManager;
import com.git.hui.jobclaw.core.preference.AiUserPreferenceProperties;
import com.git.hui.jobclaw.core.preference.repository.AiUserPreferenceService;
import com.git.hui.jobclaw.core.providers.ModelConfig;
import com.git.hui.jobclaw.core.providers.ModelProviders;
import com.git.hui.jobclaw.core.router.intent.PresetAgentIntro;
import com.git.hui.jobclaw.core.utils.SensitiveUtil;
import com.git.hui.jobclaw.core.utils.json.JsonUtil;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;
import org.springframework.beans.BeanUtils;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 个人偏好设置的业务Agent
 * - 支持修改默认的模型
 * - 支持添加不同渠道的机器人
 *
 * @date 2026/4/18
 */
@Component
public class PreferenceSettingBizAgent extends AbsBizAgent {

    private final List<ChannelBinder> channelBinders;
    private final AiUserPreferenceProperties aiUserPreferenceProperties;

    private final AiUserPreferenceService aiUserPreferenceService;


    public PreferenceSettingBizAgent(ModelProviders modelProviders,
                                     List<ChannelBinder> channelBinders,
                                     AiUserPreferenceProperties aiUserPreferenceProperties,
                                     ChatMemory chatMemory,
                                     IIdentityAgent identityAgent,
                                     AiUserPreferenceService aiUserPreferenceService) {
        super(modelProviders, chatMemory, identityAgent);
        this.channelBinders = channelBinders;
        this.aiUserPreferenceProperties = aiUserPreferenceProperties;
        this.aiUserPreferenceService = aiUserPreferenceService;
    }

    @Override
    public AgentPermission permission() {
        return AgentPermission.TOTAL;
    }

    @Override
    public AgentIntro getAgentIntro() {
        return PresetAgentIntro.PREFERENCE_SETTING;
    }

    @Override
    public List<AgentIntro> getSupportedIntents() {
        return List.of(PresetAgentIntro.PREFERENCE_SETTING, PresetAgentIntro.DEFAULT);
    }

    @Override
    public String getSystemPrompt() {
        return """
                你专门为用户的设置服务，你可以通过提供的工具来查询、修改、新增用户的偏好配置
                请根据用户的意图，选择合适的工具来执行具体的设置相关请求
                """;
    }

    @Override
    public ToolCallback[] getTools() {
        return ToolCallbacks.from(this);
    }

    @Override
    public String process(UserConversationInfo userConversationInfo, ChannelReceiveMessage message) {
        if (!NumberUtil.isNumber(userConversationInfo.jobClawUserId())) {
            return "仅绑定JobClaw账号的用户才可以使用服务偏好配置Agent哦~";
        }

        return llmCaller.call(userConversationInfo, new Prompt(message.getMessage()));
    }

    @Override
    public Flux<LlmRspCell> stream(UserConversationInfo userConversationInfo, ChannelReceiveMessage message) {
        if (!NumberUtil.isNumber(userConversationInfo.jobClawUserId())) {
            return Flux.just(new LlmRspCell(null, "仅绑定JobClaw账号的用户才可以使用服务偏好配置Agent哦~", null));
        }

        return llmCaller.stream(userConversationInfo, new Prompt(message.getMessage()), LlmRspCell::of);
    }


    @Tool(description = "修改用户的默认模型")
    public String updateActiveModel(
            @JsonPropertyDescription("模型提供方，如 deepseek, silicon, ali, openai, doubao")
            String provider,
            @JsonPropertyDescription("模型名称，如 deepseek-flash")
            String model,
            @JsonPropertyDescription("模型类型, 如 TXT, VISION, IMAGE, VIDEO, EMBEDDING, ASR, TTS")
            ModelConfig.ModelType type,
            ToolContext toolContext) {
        UserConversationInfo user = llmCaller.getUser(toolContext);
        String jobClawUserId = user.jobClawUserId();

        AiUserPreferenceProperties.UserPreferenceEntry entry = preferenceForUser(jobClawUserId);
        var modelPreference = entry.getModels();
        switch (type) {
            case TEXT:
                modelPreference.setText(provider + "#" + model);
                break;
            case VISION:
                modelPreference.setVision(provider + "#" + model);
                break;
            case IMAGE:
                modelPreference.setImage(provider + "#" + model);
                break;
            case VIDEO:
                modelPreference.setVideo(provider + "#" + model);
                break;
            case EMBEDDING:
                modelPreference.setEmbedding(provider + "#" + model);
                break;
            case ASR:
                modelPreference.setAsr(provider + "#" + model);
                break;
            case TTS:
                modelPreference.setTts(provider + "#" + model);
                break;
            default:
                throw new IllegalArgumentException("不支持的模型类型: " + type);
        }

        aiUserPreferenceService.saveOrUpdate(Long.parseLong(jobClawUserId), JsonUtil.toStr(entry));
        return "设置成功";
    }

    @Tool(description = "添加大模型访问的APIKEY")
    public String addModelApiKey(
            @JsonPropertyDescription("模型提供方，如 deepseek, silicon, ali, openai, doubao")
            String provider,
            @JsonPropertyDescription("模型APIKEY")
            String apiKey,
            ToolContext toolContext) {
        UserConversationInfo user = llmCaller.getUser(toolContext);
        String jobClawUserId = user.jobClawUserId();
        AiUserPreferenceProperties.UserPreferenceEntry entry = preferenceForUser(jobClawUserId);
        var tag = entry.getProviders().get(provider);
        if (tag == null) {
            return "您现在还没有维护这个供应商" + provider + "的配置信息哦，请到 我的 -> 个人信息 -> 偏好设置中进行维护!";
        }

        tag.setApiKey(apiKey);
        aiUserPreferenceService.saveOrUpdate(Long.parseLong(jobClawUserId), JsonUtil.toStr(entry));
        return "设置成功";
    }

    @Tool(description = "查询显示用户的当前个人偏好设置")
    public AiUserPreferenceProperties.UserPreferenceEntry showMyPreference(ToolContext toolContext) {
        UserConversationInfo user = llmCaller.getUser(toolContext);
        String jobClawUserId = user.jobClawUserId();
        AiUserPreferenceProperties.UserPreferenceEntry entry = preferenceForUser(jobClawUserId);
        return securityReturn(entry);
    }

    // AI-GENERATED AIDEV-NOTE: 新用户继承模型名称；不复制全局密钥，不修改共享默认项。
    private AiUserPreferenceProperties.UserPreferenceEntry preferenceForUser(String userId) {
        var source = aiUserPreferenceProperties.getUserPreference(userId);
        var entry = new AiUserPreferenceProperties.UserPreferenceEntry();
        if (source != null) BeanUtils.copyProperties(source, entry);
        entry.setUserId(userId);
        var models = new AiUserPreferenceProperties.UserModelPreference();
        var defaults = aiUserPreferenceProperties.getUserPreference(ModelProviders.DEFAULT_PREFERENCE);
        if (defaults != null && defaults.getModels() != null) BeanUtils.copyProperties(defaults.getModels(), models);
        if (source != null && source.getModels() != null) {
            var original = source.getModels();
            if (original.getText() != null) models.setText(original.getText());
            if (original.getVision() != null) models.setVision(original.getVision());
            if (original.getImage() != null) models.setImage(original.getImage());
            if (original.getVideo() != null) models.setVideo(original.getVideo());
            if (original.getEmbedding() != null) models.setEmbedding(original.getEmbedding());
            if (original.getAsr() != null) models.setAsr(original.getAsr());
            if (original.getTts() != null) models.setTts(original.getTts());
        }
        entry.setModels(models);
        entry.setProviders(source == null || source.getProviders() == null ? new HashMap<>() : new HashMap<>(source.getProviders()));
        return entry;
    }

    private AiUserPreferenceProperties.UserPreferenceEntry securityReturn(AiUserPreferenceProperties.UserPreferenceEntry entry) {
        AiUserPreferenceProperties.UserPreferenceEntry ret = new AiUserPreferenceProperties.UserPreferenceEntry();
        ret.setUserId(entry.getUserId());
        ret.setCollector(entry.getCollector());
        ret.setChannels(entry.getChannels());
        ret.setModels(entry.getModels());

        Map<String, AiUserPreferenceProperties.ProviderConfig> providers = new HashMap<>();
        for (var kv : entry.getProviders().entrySet()) {
            var config = kv.getValue();

            AiUserPreferenceProperties.ProviderConfig provider = new AiUserPreferenceProperties.ProviderConfig();
            provider.setApiStyle(config.getApiStyle());
            provider.setApiKey(SensitiveUtil.securityReturn(config.getApiKey()));
            provider.setModels(config.getModels());

            providers.put(kv.getKey(), provider);
        }
        ret.setProviders(providers);
        return ret;
    }

}
