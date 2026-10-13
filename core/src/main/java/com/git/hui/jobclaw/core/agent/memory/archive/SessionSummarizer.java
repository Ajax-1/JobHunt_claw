package com.git.hui.jobclaw.core.agent.memory.archive;

import com.git.hui.jobclaw.core.agent.llm.LlmCaller;
import com.git.hui.jobclaw.core.agent.memory.ContextWindowProperties;
import com.git.hui.jobclaw.core.agent.models.UserConversationInfo;
import com.git.hui.jobclaw.core.utils.FileUtils;
import com.git.hui.jobclaw.core.utils.files.YamlDocument;
import com.git.hui.jobclaw.core.utils.files.YamlParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Session summarizer that generates concise summaries of conversation history.
 *
 * <p>Features:
 * <ul>
 *   <li>Async summary generation to avoid blocking</li>
 *   <li>Fallback to empty summary on failure</li>
 *   <li>Configurable trigger conditions</li>
 *   <li>Prompt template based</li>
 * </ul>
 *
 * <p>AIDEV-NOTE: Core component for Phase 2 session summarization
 */
@Component
public class SessionSummarizer {

    private static final Logger log = LoggerFactory.getLogger(SessionSummarizer.class);

    private static final int MAX_SUMMARY_LENGTH = 100;
    private static final int MAX_CONVERSATION_FOR_SUMMARY = 30;

    private final String promptTemplate;
    private final ContextWindowProperties properties;
    private final Path conversationsDir;

    private final ConversationArchiveRepository archiveRepository;

    private final LlmCaller llmCaller;

    public SessionSummarizer(
            @Value("${agent.workspace:Unknown}") Resource workspaceDir,
            ContextWindowProperties properties,
            @Value("classpath:/prompts/session-summary-prompt.md") Resource promptResource,
            ConversationArchiveRepository archiveRepository, LlmCaller simpleLlmCaller) throws IOException {
        this.conversationsDir = workspaceDir.getFile().toPath().resolve("conversations");
        this.properties = properties;
        this.archiveRepository = archiveRepository;
        this.llmCaller = simpleLlmCaller;

        // Load prompt template
        try {
            this.promptTemplate = promptResource.getContentAsString(StandardCharsets.UTF_8);
            log.info("SessionSummarizer initialized with prompt template");
        } catch (IOException e) {
            log.error("Failed to load session summary prompt template", e);
            throw new RuntimeException("Failed to initialize SessionSummarizer", e);
        }
    }


    /**
     * Generate summary asynchronously.
     *
     * @param messages conversation messages to summarize
     * @return CompletableFuture with summary text
     */
    public void autoAsyncSummarize(UserConversationInfo conversation, List<Message> messages, Consumer<List<Message>> call) {
        if (!shouldSummarize(messages)) {
            return;
        }
        CompletableFuture.runAsync(() -> {
            log.info("Generating summary for conversation: {} ({} messages)", conversation, messages.size());
            try {
                // Split into old (to compress) and recent (to keep)
                int splitIndex = messages.size() - properties.getKeepRecent();
                List<Message> oldMessages = messages.subList(0, splitIndex);
                List<Message> recentMessages = messages.subList(splitIndex, messages.size());

                // Extract existing system messages from old messages (preserve them)
                List<Message> oldNonSystem = oldMessages.stream()
                        .filter(m -> !(m instanceof SystemMessage))
                        .toList();

                // Only compress if there are enough non-system messages to summarize
                if (oldNonSystem.size() < 4) {
                    return;
                }

                log.info("[Compress] Compressing {} old messages, keeping {} recent", oldNonSystem.size(), recentMessages.size());

                try {
                    // Generate summary of old messages
                    String summary = summarize(conversation, oldNonSystem);
                    if (summary != null && !summary.isBlank()) {
                        // Save summary to file
                        saveSummary(conversation, summary, oldNonSystem);
                        // Archive the old messages before discarding them
                        archiveRepository.archive(conversation, oldNonSystem, summary);
                        // Discard old messages
                        call.accept(recentMessages);
                    }
                } catch (Exception e) {
                    log.warn("[Compress] Failed to compress old messages, falling back to normal truncation", e);
                }
            } catch (Exception e) {
                log.error("Failed to generate summary, keeping existing", e);
                // Keep existing summary on failure
            }
        });
    }

    private void saveSummary(UserConversationInfo conversation, String summary, List<Message> messages) {
        // 将会话摘要，保存到对应的文件中
        Map<String, String> frontmatter = new LinkedHashMap<>();
        frontmatter.put("channel", conversation.channel());
        frontmatter.put("conversationId", conversation.conversationId());
        frontmatter.put("updatedAt", Instant.now().toString());
        frontmatter.put("summary", summary);

        // 保存会话摘要
        Path file = resolveFile(conversation);
        FileUtils.ensureDirectory(file.getParent());

        String body = "summary for " + messages.size() + " message!";
        String content = YamlParser.serialize(new YamlDocument(frontmatter, body));
        try {
            Files.writeString(file, content, StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save conversation: " + conversation, e);
        }
    }


    public String getSummaryInfo(UserConversationInfo conversation) {
        Path file = resolveFile(conversation);

        if (Files.exists(file)) {
            try {
                Map<String, String> existing = YamlParser.parse(Files.readString(file)).frontmatter();
                return existing.get("createdAt");
            } catch (IOException ignored) {
            }
        }
        return null;
    }

    private Path resolveFile(UserConversationInfo conversation) {
        return conversationsDir.resolve(conversation.jobClawUserId())
                .resolve("summary-" + conversation.channel() + "-" + conversation.conversationId() + ".yaml");
    }

    /**
     * Check if summary should be generated based on conversation history.
     *
     * todo 可以借助大模型的能力，来判断新增的对话是否需要触发摘要重新生成；现在这里只根据长度 + 频率做了一个简单的控制
     *
     * @param messages current conversation messages
     * @return true if summary should be generated
     */
    public boolean shouldSummarize(List<Message> messages) {
        if (messages == null || messages.isEmpty() || !properties.isSummaryEnabled()) {
            return false;
        }

        // Trigger summary if message count exceeds threshold
        int messageCount = messages.size();
        int triggerThreshold = Math.max(properties.getKeepRecent() * 2, 10);

        boolean shouldSummarize = messageCount > triggerThreshold && messages.size() % properties.getSummaryUpdateFrequency() == 0;

        if (shouldSummarize) {
            log.debug("Should summarize: {} messages > threshold {}", messageCount, triggerThreshold);
        }

        return shouldSummarize;
    }


    /**
     * Generate summary synchronously.
     *
     * @param messages conversation messages to summarize
     * @return summary text, or empty string if failed
     */
    public String summarize(UserConversationInfo userConversationInfo, List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return "";
        }

        try {
            log.info("Generating summary for {} messages", messages.size());

            // Prepare conversation text
            String conversationText = formatConversation(messages);

            // Build prompt
            String prompt = promptTemplate.replace("{conversation_history}", conversationText);

            // Call AI to generate summary
            String summary = llmCaller.call(userConversationInfo, new Prompt(prompt));

            // Validate and clean summary
            summary = validateSummary(summary);

            log.info("Summary generated successfully ({} chars): {}", summary.length(),
                    summary.substring(0, Math.min(50, summary.length())));

            return summary;

        } catch (Exception e) {
            log.error("Failed to generate summary", e);
            // Fallback to empty summary
            return "";
        }
    }

    /**
     * Format conversation messages into text for summarization.
     *
     * @param messages conversation messages
     * @return formatted conversation text
     */
    private String formatConversation(List<Message> messages) {
        // Limit to recent messages to avoid overwhelming the model
        List<Message> recentMessages = messages.size() > MAX_CONVERSATION_FOR_SUMMARY
                ? messages.subList(messages.size() - MAX_CONVERSATION_FOR_SUMMARY, messages.size())
                : messages;

        return recentMessages.stream()
                .map(msg -> {
                    String role = msg.getMessageType().getValue();
                    String text = msg.getText();
                    // Truncate very long messages
                    if (text.length() > 200) {
                        text = text.substring(0, 200) + "...";
                    }
                    return role.toUpperCase() + ": " + text;
                })
                .collect(Collectors.joining("\n"));
    }

    /**
     * Validate and clean the generated summary.
     *
     * @param summary raw summary text
     * @return validated summary
     */
    private String validateSummary(String summary) {
        if (summary == null || summary.isBlank()) {
            return "";
        }

        // Trim whitespace
        summary = summary.trim();

        // Remove any prefix like "Summary:" or "摘要:"
        summary = summary.replaceAll("^(摘要[:：]?|Summary[:：]?)\\s*", "");

        // Truncate if too long
        if (summary.length() > MAX_SUMMARY_LENGTH) {
            summary = summary.substring(0, MAX_SUMMARY_LENGTH - 3) + "...";
            log.warn("Summary truncated from original length");
        }

        return summary;
    }

    /**
     * Create a system message containing the summary.
     *
     * @param summary summary text
     * @return system message with summary, or null if summary is empty
     */
    public Message createSummaryMessage(String summary) {
        if (summary == null || summary.isBlank()) {
            return null;
        }

        String systemPrompt = String.format(
                "[对话摘要] 以下是之前对话的摘要，请基于此继续对话：%s",
                summary
        );

        return new UserMessage(systemPrompt);
    }
}
