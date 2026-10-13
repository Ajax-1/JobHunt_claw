package com.git.hui.jobclaw.core.cli;

import cn.hutool.core.util.NumberUtil;
import com.git.hui.jobclaw.core.agent.models.UserConversationInfo;
import com.git.hui.jobclaw.core.channel.ChannelReceiveMessage;
import com.git.hui.jobclaw.core.preference.AiUserPreferenceProperties;
import com.git.hui.jobclaw.core.preference.repository.AiUserPreferenceService;
import com.git.hui.jobclaw.core.providers.ModelConfig;
import com.git.hui.jobclaw.core.providers.ModelProviders;
import com.git.hui.jobclaw.core.router.intent.PresetAgentIntro;
import com.git.hui.jobclaw.core.utils.json.JsonUtil;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * 模型命令处理器 (/model)
 *
 * AIDEV-NOTE: 查询和切换用户默认模型
 *
 * @date 2026/7/4
 */
@Component
public class ModelCommandHandler implements SystemCommandHandler {
    private final ModelProviders modelProviders;
    private final AiUserPreferenceProperties aiUserPreferenceProperties;
    private final AiUserPreferenceService aiUserPreferenceService;

    public ModelCommandHandler(ModelProviders modelProviders,
                               AiUserPreferenceProperties aiUserPreferenceProperties,
                               AiUserPreferenceService aiUserPreferenceService) {
        this.modelProviders = modelProviders;
        this.aiUserPreferenceProperties = aiUserPreferenceProperties;
        this.aiUserPreferenceService = aiUserPreferenceService;
    }

    @Override
    public boolean supports(String command) {
        return "/model".equals(command) || command.startsWith("/model ");
    }

    @Override
    public boolean handle(ChannelReceiveMessage msg,
                          UserConversationInfo conversationInfo,
                          String command,
                          Function<String, Boolean> process) {
        String userId = conversationInfo.jobClawUserId();
        if (!NumberUtil.isNumber(userId)) {
            return process.apply("仅绑定JobClaw账号的用户才可以切换默认模型。");
        }

        String arg = command.length() <= "/model".length() ? "" : command.substring("/model".length()).trim();
        if (!StringUtils.hasText(arg)) {
            return process.apply(buildModelList(userId));
        }
        return process.apply(switchModel(userId, arg));
    }

    @Override
    public PresetAgentIntro getIntentType() {
        return PresetAgentIntro.MODEL;
    }

    @Override
    public String getDescription() {
        return "查看和切换默认模型，示例：/model 或 /model deepseek#deepseek-flash";
    }

    private String buildModelList(String userId) {
        String textModel = safeModelSelection(userId, ModelConfig.ModelType.TEXT);
        String visionModel = safeModelSelection(userId, ModelConfig.ModelType.VISION);
        List<ModelConfig.ModelInfo> models = availableChatModels();

        StringBuilder sb = new StringBuilder();
        sb.append("当前默认模型\n\n");
        sb.append("- 文本：").append(displayModel(textModel)).append("\n");
        sb.append("- 视觉：").append(displayModel(visionModel)).append("\n\n");
        sb.append("可用模型\n\n");

        if (models.isEmpty()) {
            sb.append("暂无可用模型，请先在后台 LLM供应商 中配置文本或视觉模型。");
            return sb.toString();
        }

        for (ModelConfig.ModelInfo model : models) {
            String selection = selection(model);
            sb.append("- ")
                    .append(selection)
                    .append("（")
                    .append(typeLabel(model.getType()))
                    .append("）");
            if (selection.equals(textModel)) {
                sb.append(" 默认文本");
            }
            if (selection.equals(visionModel)) {
                sb.append(" 默认视觉");
            }
            sb.append("\n");
        }

        sb.append("\n使用 `/model modelName` 切换默认模型；模型名不唯一时请使用 `provider#modelName`。");
        return sb.toString();
    }

    private String switchModel(String userId, String arg) {
        ModelConfig.ModelType requestedType = null;
        String lookup = arg.trim();
        String lower = lookup.toLowerCase(Locale.ROOT);
        if (lower.startsWith("text ")) {
            requestedType = ModelConfig.ModelType.TEXT;
            lookup = lookup.substring("text ".length()).trim();
        } else if (lower.startsWith("vision ")) {
            requestedType = ModelConfig.ModelType.VISION;
            lookup = lookup.substring("vision ".length()).trim();
        }

        List<ModelConfig.ModelInfo> matches = findMatches(lookup, requestedType);
        if (matches.isEmpty()) {
            return "未找到模型：" + lookup + "\n\n发送 `/model` 可以查看当前可用模型。";
        }
        if (matches.size() > 1) {
            StringBuilder sb = new StringBuilder("模型名不唯一，请使用完整 provider#modelName：\n\n");
            for (ModelConfig.ModelInfo model : matches) {
                sb.append("- ").append(selection(model)).append("（").append(typeLabel(model.getType())).append("）\n");
            }
            return sb.toString();
        }

        ModelConfig.ModelInfo selected = matches.get(0);
        AiUserPreferenceProperties.UserPreferenceEntry preference = mutablePreference(userId);
        AiUserPreferenceProperties.UserModelPreference modelPreference = preference.getModels();
        String selection = selection(selected);
        if (selected.getType() == ModelConfig.ModelType.VISION) {
            modelPreference.setVision(selection);
        } else {
            modelPreference.setText(selection);
        }

        aiUserPreferenceService.saveOrUpdate(Long.parseLong(userId), JsonUtil.toStr(preference));
        modelProviders.clearCurrentModelInfo(userId);
        return "已切换默认" + typeLabel(selected.getType()) + "模型：" + selection;
    }

    private List<ModelConfig.ModelInfo> findMatches(String lookup, ModelConfig.ModelType requestedType) {
        String normalized = lookup.toLowerCase(Locale.ROOT);
        boolean fullSelection = lookup.contains("#");
        return availableChatModels().stream()
                .filter(model -> requestedType == null || model.getType() == requestedType)
                .filter(model -> {
                    if (fullSelection) {
                        return selection(model).equalsIgnoreCase(lookup);
                    }
                    return model.getModelName() != null && model.getModelName().toLowerCase(Locale.ROOT).equals(normalized);
                })
                .toList();
    }

    private List<ModelConfig.ModelInfo> availableChatModels() {
        return modelProviders.listGlobalModelInfos().stream()
                .filter(model -> model.getType() == ModelConfig.ModelType.TEXT || model.getType() == ModelConfig.ModelType.VISION)
                .sorted(Comparator.comparing(ModelCommandHandler::selection, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private AiUserPreferenceProperties.UserPreferenceEntry mutablePreference(String userId) {
        AiUserPreferenceProperties.UserPreferenceEntry source = aiUserPreferenceProperties.getUserPreference(userId);
        if (source == null) {
            source = aiUserPreferenceProperties.getUserPreference(ModelProviders.DEFAULT_PREFERENCE);
        }

        AiUserPreferenceProperties.UserPreferenceEntry preference = source == null
                ? new AiUserPreferenceProperties.UserPreferenceEntry()
                : JsonUtil.toObj(JsonUtil.toStr(source), AiUserPreferenceProperties.UserPreferenceEntry.class);
        preference.setUserId(userId);
        if (preference.getModels() == null) {
            preference.setModels(new AiUserPreferenceProperties.UserModelPreference());
        }
        if (!StringUtils.hasText(preference.getModels().getText())) {
            preference.getModels().setText(safeDefaultSelection(ModelConfig.ModelType.TEXT));
        }
        if (!StringUtils.hasText(preference.getModels().getVision())) {
            preference.getModels().setVision(safeDefaultSelection(ModelConfig.ModelType.VISION));
        }
        return preference;
    }

    private String safeModelSelection(String userId, ModelConfig.ModelType type) {
        try {
            return modelProviders.getModelSelection(userId, type);
        } catch (Exception e) {
            return null;
        }
    }

    private String safeDefaultSelection(ModelConfig.ModelType type) {
        try {
            return modelProviders.getDefaultModelSelection(type);
        } catch (Exception e) {
            return null;
        }
    }

    private static String selection(ModelConfig.ModelInfo model) {
        return model.getProvider() + "#" + model.getModelName();
    }

    private static String displayModel(String model) {
        return StringUtils.hasText(model) ? model : "未配置";
    }

    private static String typeLabel(ModelConfig.ModelType type) {
        if (type == ModelConfig.ModelType.VISION) {
            return "视觉";
        }
        return "文本";
    }
}
