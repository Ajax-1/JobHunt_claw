package com.git.hui.jobclaw.core.agent.llm;

import com.git.hui.jobclaw.core.agent.IIdentityAgent;
import com.git.hui.jobclaw.core.agent.models.UserConversationInfo;
import com.git.hui.jobclaw.core.agent.react.ReActAdvisor;
import com.git.hui.jobclaw.core.channel.ChannelReceiveMessage;
import com.git.hui.jobclaw.core.configuration.ConfigurationManager;
import com.git.hui.jobclaw.core.providers.ModelConfig;
import com.git.hui.jobclaw.core.providers.ModelProviders;
import com.git.hui.jobclaw.core.tasks.TaskManager;
import com.git.hui.jobclaw.core.tools.AutoDiscoveredTool;
import com.git.hui.jobclaw.core.tools.CheckListTool;
import com.git.hui.jobclaw.core.tools.McpTool;
import com.git.hui.jobclaw.core.tools.TaskTool;
import com.git.hui.jobclaw.core.utils.SpringUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springaicommunity.agent.tools.FileSystemTools;
import org.springaicommunity.agent.tools.ShellTools;
import org.springaicommunity.agent.tools.SkillsTool;
import org.springaicommunity.agent.tools.SmartWebFetchTool;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.core.io.Resource;
import org.springframework.util.CollectionUtils;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 会自动注入用户偏好的LlmCaller
 * 适用于基于个人信息的推荐、对话场景
 *
 * @date 2026/4/9
 */
@Slf4j
public class UserPreferenceBasedLlmCaller extends BizAgentLlmCaller {
    private final TaskManager taskManager;

    private final List<AutoDiscoveredTool<?>> autoDiscoveredTools;

    public UserPreferenceBasedLlmCaller(ModelProviders modelProviders,
                                        IIdentityAgent identityAgent,
                                        ChatMemory chatMemory,
                                        TaskManager taskManager,
                                        List<AutoDiscoveredTool<?>> autoDiscoveredTools,
                                        String systemPrompt,
                                        ToolCallback... tools) {
        super(chatMemory, identityAgent, modelProviders, systemPrompt, tools);
        this.taskManager = taskManager;
        this.autoDiscoveredTools = autoDiscoveredTools;
    }

    public <T> Flux<T> stream(UserConversationInfo conversationInfo, ChannelReceiveMessage message, Function<ChatResponse, T> func) {
        String jobClawUserId = conversationInfo.jobClawUserId();
        Prompt prompt = buildSoulPrompt(jobClawUserId, message);
        return getClient(conversationInfo, prompt).prompt(prompt)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationInfo.genId()))
                .toolContext(Map.of("jobClawUserId", jobClawUserId, "user", conversationInfo))
                .stream()
                .chatResponse()
                .map(func);
    }

    public String call(UserConversationInfo conversationInfo, ChannelReceiveMessage message) {
        // Execute with conversation memory
        String jobClawUserId = conversationInfo.jobClawUserId();
        Prompt prompt = buildSoulPrompt(jobClawUserId, message);
        return getClient(conversationInfo, prompt).prompt(prompt)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationInfo.genId()))
                .toolContext(Map.of("jobClawUserId", jobClawUserId, "user", conversationInfo))
                .call().content();
    }


    /**
     * 根据用户 + 会话，获取对应的LLM客户端
     * @param user 用户对话信息
     * @return
     */
    protected ChatClient getClient(UserConversationInfo user, Prompt prompt) {
        // 判断message中是否包含media
        ModelConfig.ModelType model = ModelConfig.ModelType.TEXT;
        if (prompt.getUserMessages().stream().anyMatch(m -> !CollectionUtils.isEmpty(m.getMedia()))) {
            model = ModelConfig.ModelType.VISION;
        }

        boolean multiModal = model == ModelConfig.ModelType.VISION;
        String userId = user.jobClawUserId();
        String channel = user.channel();

        String key;
        if (StringUtils.isBlank(channel)) {
            key = multiModal ? userId + "_m" : userId;
        } else {
            key = multiModal ? userId + "_" + channel + "_m" : userId + "_" + channel;
        }
        var client = super.chatClientMap.get(key);
        if (client == null) {
            client = initClient(userId, multiModal);
            log.info("[{}] init UserPreferenceBasedLlmCaller for user: {}, channel: {}", user.agent(), user.jobClawUserId(), user.channel());
            super.chatClientMap.put(key, client);
        } else {
            modelProviders.getModel(userId, multiModal ? ModelConfig.ModelType.VISION : ModelConfig.ModelType.TEXT);
        }
        return client;
    }

    public Prompt buildSoulPrompt(String jobClawUserId, String defaultSystemPrompt, String question) {
        List<Message> messages = new ArrayList<>();

        // Add system message with identity documents
        String systemPrompt = identityAgent.buildSystemPrompt(jobClawUserId);
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(new SystemMessage(systemPrompt + "\n\n" + defaultSystemPrompt));
        } else if (StringUtils.isNotBlank(defaultSystemPrompt)) {
            messages.add(new SystemMessage(defaultSystemPrompt));
        }

        // Add user message
        messages.add(UserMessage.builder().text(question).build());

        return new Prompt(messages);
    }


    /**
     * Build prompt with system identity documents for multi-modal message.
     */
    public Prompt buildSoulPrompt(String jobClawUserId, ChannelReceiveMessage message) {
        // AI-GENERATED AIDEV-NOTE: 用户画像与业务职责同时注入，不能丢失 Agent 的工具使用规则。
        return buildSoulPrompt(jobClawUserId, this.systemPrompt, message);
    }

    public Prompt buildSoulPrompt(String jobClawUserId, String defaultSystemPrompt, ChannelReceiveMessage message) {
        List<org.springframework.ai.chat.messages.Message> messages = new ArrayList<>();

        // Add system message with identity documents
        String systemPrompt = identityAgent.buildSystemPrompt(jobClawUserId);
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            if (defaultSystemPrompt != null) {
                messages.add(new SystemMessage(systemPrompt + "\n\n" + defaultSystemPrompt));
            } else {
                messages.add(new SystemMessage(systemPrompt));
            }
        } else if (StringUtils.isNotBlank(defaultSystemPrompt)) {
            messages.add(new SystemMessage(defaultSystemPrompt));
        }

        // Add user message with media
        if (message == null) {
            throw new IllegalArgumentException("Message cannot be null");
        }

        var userMessage = buildUserMessage(message);
        messages.add(userMessage);
        return new Prompt(messages);
    }

    private ChatClient initClient(String userId, boolean multiModal) {
        try {
            String agentPrompt = identityAgent.buildSystemPrompt(userId);
            var defaultSystem = buildSystemPrompt(agentPrompt);
            var model = modelProviders.getModel(userId, multiModal ? ModelConfig.ModelType.VISION : ModelConfig.ModelType.TEXT);
            var chatModel = (ChatModel) model;
            var chatClientBuilder = ChatClient.builder(chatModel);

            // 禁用 Spring AI 的自动工具执行，由 ReActAdvisor 自行控制 ReAct 循环
            chatClientBuilder.defaultOptions(
                    ToolCallingChatOptions.builder().internalToolExecutionEnabled(false).build()
            );

            // 使用自定义 ReActAdvisor 替代 ToolCallAdvisor，支持 Middleware 生命周期拦截
            var reactBuilder = ReActAdvisor.builder().chatModel(chatModel).autoInjectMiddleware();

            chatClientBuilder.defaultAdvisors(new SimpleLoggerAdvisor())
                    .defaultSystem(defaultSystem)
                    .defaultTools(
                            CheckListTool.builder().build(),
                            TaskTool.builder().taskManager(taskManager).build(),
                            McpTool.builder().configurationManager(SpringUtil.getBean(ConfigurationManager.class)).build(),
                            //Bash execution tool
                            ShellTools.builder().build(),// built-in shell tools
                            // Read, Write and Edit files tool // fixme 这里需要限制访问权限
                            FileSystemTools.builder().build(),// built-in file system tools
                            // Smart web fetch tool
                            SmartWebFetchTool.builder(chatClientBuilder.clone().build()).build())
                    .defaultAdvisors(
                            reactBuilder.build(),
                            MessageChatMemoryAdvisor.builder(chatMemory).build()
                    );

            // AI-GENERATED AIDEV-NOTE: Skills 可选；空目录不能阻止普通问答和岗位工具初始化。
            Path skillsDirectory = skillsDir(workspace);
            try (var files = Files.walk(skillsDirectory)) {
                if (files.anyMatch(path -> Files.isRegularFile(path) && path.getFileName().toString().equals("SKILL.md"))) {
                    chatClientBuilder.defaultToolCallbacks(SkillsTool.builder().addSkillsDirectory(skillsDirectory.toString()).build());
                }
            }

            if (autoDiscoveredTools != null && !autoDiscoveredTools.isEmpty()) {
                autoDiscoveredTools.forEach(autoDiscoveredTool -> chatClientBuilder.defaultTools(autoDiscoveredTool.tool()));
            }
            if (super.tools != null && super.tools.length > 0) {
                chatClientBuilder.defaultToolCallbacks(tools);
            }

            return chatClientBuilder.build();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static Path skillsDir(Resource workspace) throws IOException {
        Path skillsDir = workspace.getFile().toPath().resolve("skills");
        Files.createDirectories(skillsDir);
        return skillsDir;
    }
}
