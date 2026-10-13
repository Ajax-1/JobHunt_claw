package com.git.hui.jobclaw.core.agent.llm;

import com.git.hui.jobclaw.core.agent.IIdentityAgent;
import com.git.hui.jobclaw.core.agent.models.UserConversationInfo;
import com.git.hui.jobclaw.core.agent.react.ReActAdvisor;
import com.git.hui.jobclaw.core.channel.ChannelReceiveMessage;
import com.git.hui.jobclaw.core.providers.ModelConfig;
import com.git.hui.jobclaw.core.providers.ModelProviders;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.util.CollectionUtils;
import org.springframework.util.MimeType;
import reactor.core.publisher.Flux;

import java.nio.file.Path;
import java.nio.file.Files;
import java.io.IOException;
import java.util.Locale;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 业务Agent LLM调用封装:（默认不会加载用户的偏好信息，如果某个业务Agent需要加载用户的 info.md/user.md 则推荐使用 UserPreferenceBasedLlmCaller）
 * - 使用 ChatMemory 进行统一的上下文管理，对话会持久化
 * @date 2026/4/20
 */
@Slf4j
public class BizAgentLlmCaller extends SimpleLlmCaller {
    protected final ChatMemory chatMemory;
    protected final IIdentityAgent identityAgent;
    protected final Map<String, ChatClient> chatClientMap = new ConcurrentHashMap<>();

    protected final String systemPrompt;
    protected final ToolCallback[] tools;

    public BizAgentLlmCaller(ChatMemory chatMemory,
                             IIdentityAgent identityAgent,
                             ModelProviders modelProviders,
                             String systemPrompt, ToolCallback... tools) {
        super(modelProviders);
        this.identityAgent = identityAgent;
        this.chatMemory = chatMemory;
        this.systemPrompt = systemPrompt;
        this.tools = tools;
    }

    protected ChatClient getClient(UserConversationInfo user, Prompt prompt) {
        // 判断message中是否包含media
        ModelConfig.ModelType model = ModelConfig.ModelType.TEXT;
        if (prompt.getUserMessages().stream().anyMatch(m -> !CollectionUtils.isEmpty(m.getMedia()))) {
            model = ModelConfig.ModelType.VISION;
        }
        String key = user.jobClawUserId() + model.name();
        if (chatClientMap.containsKey(key)) {
            modelProviders.getModel(user.jobClawUserId(), model);
            return chatClientMap.get(key);
        }


        var chatModel = (ChatModel) modelProviders.getModel(user.jobClawUserId(), model);

        // 使用自定义 ReActAdvisor 替代 ToolCallAdvisor，支持 Middleware 生命周期拦截
        var reactBuilder = ReActAdvisor.builder().chatModel(chatModel).autoInjectMiddleware();
        var builder = ChatClient.builder(chatModel)
                .defaultOptions(
                        ToolCallingChatOptions.builder().internalToolExecutionEnabled(false).build()
                )
                .defaultAdvisors(
                        reactBuilder.build(),
                        SimpleLoggerAdvisor.builder().build(),
                        MessageChatMemoryAdvisor.builder(chatMemory).build()
                );
        if (tools != null && tools.length > 0) {
            builder.defaultToolCallbacks(tools);
        }

        String identity = identityAgent.buildSoulPrompt(user.jobClawUserId());
        Consumer<ChatClient.PromptSystemSpec> sys;
        if (StringUtils.isBlank(identity)) {
            sys = buildSystemPrompt(systemPrompt);
        } else {
            sys = buildSystemPrompt(identity + "\n\n" + systemPrompt);
        }

        if (sys != null) {
            builder.defaultSystem(sys);
        }
        var client = builder.build();
        log.info("[{}] init BizAgentLlmCaller by register {} tools with systemPrompt len: {} for {}",
                user.agent(),
                tools == null ? 0 : tools.length,
                systemPrompt == null ? 0 : systemPrompt.length(),
                user.jobClawUserId());
        chatClientMap.put(key, client);
        return client;
    }


    public String call(UserConversationInfo user, ChannelReceiveMessage msg) {
        Prompt prompt = new Prompt(buildUserMessage(msg));
        return getClient(user, prompt).prompt(prompt)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, user.genId()))
                .toolContext(Map.of("jobClawUserId", user.jobClawUserId(),
                        "user", user,
                        "msg", msg))
                .call().content();
    }

    @Override
    public String call(UserConversationInfo user, Prompt prompt) {
        return getClient(user, prompt).prompt(prompt)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, user.genId()))
                .toolContext(Map.of("jobClawUserId", user.jobClawUserId(), "user", user))
                .call().content();
    }

    public <T> Flux<T> stream(UserConversationInfo user, ChannelReceiveMessage msg, Function<ChatResponse, T> func) {
        Prompt prompt = new Prompt(buildUserMessage(msg));
        return getClient(user, prompt).prompt(prompt)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, user.genId()))
                .toolContext(Map.of(
                        "jobClawUserId", user.jobClawUserId(),
                        "user", user,
                        "msg", msg
                ))
                .stream()
                .chatResponse()
                .map(func);
    }

    @Override
    public <T> Flux<T> stream(UserConversationInfo user, Prompt prompt, Function<ChatResponse, T> func) {
        return getClient(user, prompt).prompt(prompt)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, user.genId()))
                .toolContext(Map.of("jobClawUserId", user.jobClawUserId(), "user", user))
                .stream().chatResponse()
                .map(func);
    }

    public UserConversationInfo getUser(ToolContext toolContext) {
        return (UserConversationInfo) toolContext.getContext().get("user");
    }

    public ChannelReceiveMessage getMsg(ToolContext toolContext) {
        return (ChannelReceiveMessage) toolContext.getContext().get("msg");
    }

    public void refreshCache() {
        chatClientMap.clear();
    }


    protected UserMessage buildUserMessage(ChannelReceiveMessage message) {
        // Add images as media
        List<Media> mediaList = new ArrayList<>();

        if (message.getMedias() != null) {
            for (var image : message.getMedias()) {
                try {
                    Media media = createImageMedia(image);
                    if (media != null) {
                        mediaList.add(media);
                    }
                } catch (Exception e) {
                    log.warn("Failed to load image: {}", image.getFilePath(), e);
                }
            }
        }

        StringBuilder attachmentText = new StringBuilder();
        // AI-GENERATED AIDEV-NOTE: 文本附件直接注入正文，避免错误调用视觉模型。
        if (message.getFiles() != null) {
            for (var file : message.getFiles()) {
                try {
                    Path path = file.getFilePath();
                    String name = path == null ? "" : path.getFileName().toString().toLowerCase(Locale.ROOT);
                    if (name.matches(".*\\.(txt|md|csv|json|log)$")) {
                        if (Files.size(path) > 1024 * 1024) {
                            throw new IllegalArgumentException("文本附件不能超过 1 MB");
                        }
                        attachmentText.append("\n\n<attachment>\n")
                                .append(Files.readString(path)).append("\n</attachment>");
                        continue;
                    }
                    Media media = createFileMedia(file);
                    if (media != null) {
                        mediaList.add(media);
                    }
                } catch (IOException e) {
                    throw new IllegalArgumentException("无法读取文本附件，请重新上传", e);
                }
            }
        }

        String textContent = (message.getMessage() != null && !message.getMessage().isBlank())
                ? message.getMessage()
                : "Please analyze the attached media.";
        var msgBuilder = UserMessage.builder().text(textContent + attachmentText);

        // Add media to prompt if any
        if (!mediaList.isEmpty()) {
            msgBuilder.media(mediaList);
        }
        return msgBuilder.build();
    }


    /**
     * Create Media object from ImageContent
     */
    protected Media createImageMedia(ChannelReceiveMessage.MediaMsg image) {
        if (image.getData() != null && image.getData().length > 0) {
            // Inline image data (byte array)
            MimeType mimeType = MimeType.valueOf(image.getMimeType());
            return new Media(mimeType, new ByteArrayResource(image.getData()));
        } else if (image.getFilePath() != null) {
            // Image from file path
            Path path = image.getFilePath();
            if (path.toFile().exists()) {
                MimeType mimeType = MimeType.valueOf(image.getMimeType());
                return new Media(mimeType, new FileSystemResource(path.toFile()));
            }
        }
        return null;
    }

    /**
     * Create Media object from FileContent
     */
    protected Media createFileMedia(ChannelReceiveMessage.FileMsg file) {
        if (file.getFilePath() != null) {
            Path path = file.getFilePath();
            if (path.toFile().exists()) {
                MimeType mimeType = MimeType.valueOf(file.getMimeType());
                return new Media(mimeType, new FileSystemResource(path.toFile()));
            }
        }
        return null;
    }
}
