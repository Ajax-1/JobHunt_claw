package com.git.hui.jobclaw.core.agent.llm;

import com.git.hui.jobclaw.core.agent.models.UserConversationInfo;
import com.git.hui.jobclaw.core.agent.react.ReActAdvisor;
import com.git.hui.jobclaw.core.providers.ModelConfig;
import com.git.hui.jobclaw.core.providers.ModelProviders;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springaicommunity.agent.utils.AgentEnvironment;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 由调用者自己维护上下文、历史对话、工具注册等流程
 *
 * @date 2026/4/23
 */
@Slf4j
@Component
public class SimpleLlmCaller implements LlmCaller {

    protected final ModelProviders modelProviders;
    @Setter
    @Value("${agent.workspace:Unknown}")
    protected Resource workspace;

    public SimpleLlmCaller(ModelProviders modelProviders) {
        this.modelProviders = modelProviders;
    }

    @Override
    public <T> T call(UserConversationInfo user, Prompt prompt, Class<T> clz) {
        ChatClient client = getClient(user, prompt);
        return client.prompt(prompt).call().entity(clz);
    }

    @Override
    public String call(UserConversationInfo user, Prompt prompt) {
        return getClient(user, prompt).prompt(prompt)
                .toolContext(Map.of("user", user))
                .call().content();
    }

    @Override
    public Flux<String> stream(UserConversationInfo user, Prompt prompt) {
        return getClient(user, prompt).prompt(prompt)
                .toolContext(Map.of("user", user))
                .stream().chatResponse()
                .map(s -> s.getResult().getOutput().getText());
    }

    @Override
    public <T> Flux<T> stream(UserConversationInfo user, Prompt prompt, Function<ChatResponse, T> func) {
        return getClient(user, prompt).prompt(prompt)
                .toolContext(Map.of("user", user))
                .stream().chatResponse()
                .map(func);
    }

    protected String operation(UserConversationInfo user) {
        return user.agent() == null ? "core_call" : "agent_chat";
    }


    protected ChatClient getClient(UserConversationInfo user, Prompt prompt) {
        // 判断message中是否包含media
        ModelConfig.ModelType model = ModelConfig.ModelType.TEXT;
        if (prompt.getUserMessages().stream().anyMatch(m -> !CollectionUtils.isEmpty(m.getMedia()))) {
            model = ModelConfig.ModelType.VISION;
        }
        var chatModel = (ChatModel) modelProviders.getModel(user.jobClawUserId(), model);

        var sys = buildSystemPrompt(null);

        var builder = ChatClient.builder(chatModel)
                .defaultOptions(
                        ToolCallingChatOptions.builder().internalToolExecutionEnabled(false).build()
                )
                .defaultAdvisors(
                        ReActAdvisor.builder().chatModel(chatModel).autoInjectMiddleware().build()
                );
        if (sys != null) {
            builder.defaultSystem(sys);
        }
        log.info("[{}] init SimpleLlmCaller for {}", user.agent(), user.jobClawUserId());
        return builder.build();
    }

    protected Consumer<ChatClient.PromptSystemSpec> buildSystemPrompt(String systemPrompt) {
        if (workspace != null) {
            String info;
            try {
                info = workspace.createRelative("INFO.md").getContentAsString(StandardCharsets.UTF_8) + System.lineSeparator();
            } catch (IOException e) {
                info = """
                        ## Here is important information:
                         - the environment you are running in: {ENVIRONMENT_INFO}
                         - Your workspace is in folder `./workspace` (later noted as `<workspace>`) and contains:
                           - Context and your main memory are in the `<workspace>/context` folder. Here, all context can be found and must saved. Always search in this folder first before answering or taking action. Use it to verify your answers.
                           - Tasks need to be managed via the `TaskTool` that you can use (and only via the `TaskTool`). They are saved as markdown files in the `<workspace>/tasks` folder and structured as follows:
                             - normal tasks `yyyy-MM-dd/<HHmmss>-<state>-<name>.md`
                             - recurring tasks `recurring/<name>.md`
                         - always response in Chinese Language
                                            
                        ### Tool calling
                        You have access to various tools and skills. Try to use them as much as possible.
                        """;
            }
            String finalInfo = info;
            return p -> p.text(systemPrompt + System.lineSeparator() + finalInfo).param(AgentEnvironment.ENVIRONMENT_INFO_KEY, AgentEnvironment.info());
        }
        return StringUtils.isBlank(systemPrompt) ? null : p -> p.text(systemPrompt);
    }
}
