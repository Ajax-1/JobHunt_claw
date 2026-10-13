package com.git.hui.jobclaw.gather.service.ai;

import com.git.hui.jobclaw.core.bizexception.BizException;
import com.git.hui.jobclaw.core.bizexception.StatusEnum;
import com.git.hui.jobclaw.constants.gather.GatherModelEnum;
import com.git.hui.jobclaw.constants.gather.GatherModelTypeEnum;
import com.git.hui.jobclaw.core.providers.ModelConfig;
import com.git.hui.jobclaw.core.providers.ModelProviders;
import com.git.hui.jobclaw.gather.model.ModelSelectReq;
import com.git.hui.jobclaw.gather.service.ai.impl.AbsOcChatModelApi;
import com.git.hui.jobclaw.core.utils.json.StringBaseEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import java.util.function.Function;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * @date 2025/7/18
 */
@Slf4j
@Component
public class OcAiModelContext {

    private final List<OcChatModelApi> list;

    private final ModelProviders modelProviders;

    /**
     * 注入
     *
     * @param list
     */
    public OcAiModelContext(List<OcChatModelApi> list, ModelProviders modelProviders) {
        this.list = list;
        this.modelProviders = modelProviders;
    }

    public ChatClient chatClient(ModelSelectReq req) {
        req = withDefaultModel(req);
        if (req.providerModel()) {
            ModelConfig.ModelInfo modelInfo = modelProviders.getGlobalModelInfo(req.model());
            ChatModel chatModel = (ChatModel) modelProviders.getGlobalModel(req.model(), toModelType(req.type()));
            log.info("当前选中的后台模型为：{}#{}", modelInfo.getProvider(), modelInfo.getModelName());
            return ChatClient.builder(chatModel)
                    .defaultSystem(OcChatModelApi.GATHER_SYSTEM_PROMPT)
                    .defaultOptions(ChatOptions.builder().model(modelInfo.getModelName()).build())
                    .build();
        }
        for (OcChatModelApi api : list) {
            ChatClient client = api.chatClient(req);
            if (client != null) {
                log.info("当前选中的模型为：{}", api.modelEnum());
                return client;
            }
        }
        throw new BizException(StatusEnum.MODEL_MISMATCH_SUPPORT);
    }

    /** AI-GENERATED AIDEV-NOTE: 原始材料已准备好，批量提取不继承交互会话的 MCP 工具。 */
    public Function<Prompt, ChatResponse> extractionCaller(ModelSelectReq req) {
        Pair<ChatModel, String> resolved = model(req);
        ChatClient client = ChatClient.builder(resolved.getFirst()).build();
        return prompt -> {
            Integer maxTokens = prompt.getOptions() == null ? null : prompt.getOptions().getMaxTokens();
            ChatOptions options = ChatOptions.builder().model(resolved.getSecond()).maxTokens(maxTokens).build();
            // AI-GENERATED AIDEV-NOTE: 每批显式合并选项，避免 Prompt 覆盖关闭思考配置。
            if (resolved.getSecond().equals("deepseek-flash") || resolved.getSecond().equals("deepseek-v4-pro")) {
                options = OpenAiChatOptions.builder().model(resolved.getSecond()).maxTokens(maxTokens)
                        .reasoningEffort(deepseekExtractionReasoningEffort).build();
            }
            return client.prompt(new Prompt(prompt.getInstructions(), options)).call().chatResponse();
        };
    }

    @Value("${jobclaw.gather.deepseek-reasoning-effort:none}")
    private String deepseekExtractionReasoningEffort = "none";

    public Pair<ChatModel, String> model(ModelSelectReq req) {
        req = withDefaultModel(req);
        if (req.providerModel()) {
            ModelConfig.ModelInfo modelInfo = modelProviders.getGlobalModelInfo(req.model());
            ChatModel chatModel = (ChatModel) modelProviders.getGlobalModel(req.model(), toModelType(req.type()));
            log.info("当前选中的后台模型为：{}#{}", modelInfo.getProvider(), modelInfo.getModelName());
            return Pair.of(chatModel, modelInfo.getModelName());
        }
        for (OcChatModelApi api : list) {
            Pair<ChatModel, String> model = api.model(req);
            if (model != null) {
                log.info("当前选中的模型为：{}", api.modelEnum());
                return model;
            }
        }
        throw new BizException(StatusEnum.MODEL_MISMATCH_SUPPORT);
    }


    /**
     * 默认使用的大模型
     */
    @Value("${jobclaw.mainModel:DeepSeek}")
    private String mainModel;

    /**
     * 获取默认的聊天模型
     *
     * @return
     */
    public ChatClient getMainChatClient() {
        try {
            return chatClient(ModelSelectReq.of(StringBaseEnum.getEnumByCode(GatherModelEnum.class, mainModel), GatherModelTypeEnum.CHAT_MODEL));
        } catch (Exception e) {
            return ((AbsOcChatModelApi) list.get(0)).chatClient();
        }
    }

    private ModelConfig.ModelType toModelType(GatherModelTypeEnum type) {
        return type == GatherModelTypeEnum.IMAGE_MODEL ? ModelConfig.ModelType.VISION : ModelConfig.ModelType.TEXT;
    }

    private ModelSelectReq withDefaultModel(ModelSelectReq req) {
        if (StringUtils.hasText(req.model())) {
            return req;
        }
        ModelConfig.ModelType modelType = toModelType(req.type());
        return ModelSelectReq.of(modelProviders.getDefaultModelSelection(modelType), req.type());
    }
}
