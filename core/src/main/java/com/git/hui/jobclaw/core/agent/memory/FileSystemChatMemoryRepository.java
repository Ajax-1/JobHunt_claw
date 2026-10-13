package com.git.hui.jobclaw.core.agent.memory;

import com.git.hui.jobclaw.core.agent.IIdentityAgent;
import com.git.hui.jobclaw.core.agent.memory.archive.ConversationArchiveRepository;
import com.git.hui.jobclaw.core.agent.memory.archive.SessionSummarizer;
import com.git.hui.jobclaw.core.agent.memory.episodic.FileSystemEpisodicMemory;
import com.git.hui.jobclaw.core.agent.models.UserConversationInfo;
import com.git.hui.jobclaw.core.utils.FileUtils;
import com.git.hui.jobclaw.core.utils.MD5Utils;
import com.git.hui.jobclaw.core.utils.files.YamlDocument;
import com.git.hui.jobclaw.core.utils.files.YamlParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.memory.AppendableChatMemoryRepository;
import org.springframework.ai.chat.messages.Message;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Persists chat conversation history as YAML files inside the agent workspace.
 *
 * <p>The conversation ID is the channel name, e.g. {@code web} or
 * {@code telegram-123456789}. The repository maps this to a flat file:
 * {@code {workspace}/conversations/{jobClawUserId}/chat-{channel}.yaml}
 *
 * <p>Each file has a frontmatter block with timestamps and a body containing the
 * message list:
 * <pre>
 * ---
 * createdAt: 2026-03-21T10:00:00Z
 * updatedAt: 2026-03-21T10:05:30Z
 * ---
 * - user: |
 *     Question text
 * - assistant: |
 *     Answer text
 * </pre>
 */
@Component
public class FileSystemChatMemoryRepository implements AppendableChatMemoryRepository {

    private static final Logger log = LoggerFactory.getLogger(FileSystemChatMemoryRepository.class);

    private final Path conversationsDir;
    private final SmartWindowChatMemory smartWindow;
    private final SessionSummarizer sessionSummarizer;
    private final IIdentityAgent identityAgent;
    private final ContextWindowProperties contextWindowProperties;
    private final FileSystemEpisodicMemory episodicMemory;
    private final ConversationArchiveRepository archiveRepository;

    public FileSystemChatMemoryRepository(
            @Value("${agent.workspace:Unknown}") Resource workspaceDir,
            SmartWindowChatMemory smartWindow,
            SessionSummarizer sessionSummarizer,
            IIdentityAgent identityAgent,
            ContextWindowProperties contextWindowProperties,
            FileSystemEpisodicMemory episodicMemory,
            ConversationArchiveRepository archiveRepository) throws IOException {
        this.conversationsDir = workspaceDir.getFile().toPath().resolve("conversations");
        this.smartWindow = smartWindow;
        this.sessionSummarizer = sessionSummarizer;
        this.identityAgent = identityAgent;
        this.contextWindowProperties = contextWindowProperties;
        this.episodicMemory = episodicMemory;
        this.archiveRepository = archiveRepository;
    }

    @Override
    public List<String> findConversationIds() {
        if (!Files.exists(conversationsDir)) return List.of();
        try (Stream<Path> files = Files.list(conversationsDir)) {
            List<String> total = new ArrayList<>();
            for (Path p : files.toList()) {
                try (var sub = Files.list(p)) {
                    total.addAll(
                            sub.map(t -> t.getFileName().toString())
                                    .filter(name -> name.startsWith("chat-") && name.endsWith(".yaml"))
                                    .map(name -> name.substring("chat-".length(), name.length() - ".yaml".length()))
                                    .toList()
                    );
                }
            }
            return total;
        } catch (IOException e) {
            throw new RuntimeException("Failed to list conversations", e);
        }
    }

    @Override
    public List<Message> findByConversationId(String conversationId) {
        Path file = resolveFile(conversationId);
        if (!Files.exists(file)) return List.of();
        try {
            YamlDocument doc = YamlParser.parse(Files.readString(file));
            List<Message> allMessages = ChatYamlSerializer.deserialize(doc.body());

            // Parse conversation info for archiving
            UserConversationInfo conversationInfo = UserConversationInfo.parse(conversationId);

            // Apply smart window management (Phase 3: includes compress-before-drop)
            List<Message> managedMessages = smartWindow.manage(allMessages);

            List<Message> result = new ArrayList<>();

            // Inject session summary if available
            String summary = sessionSummarizer.getSummaryInfo(conversationInfo);
            if (summary != null && !summary.isBlank()) {
                Message summaryMessage = sessionSummarizer.createSummaryMessage(summary);
                if (summaryMessage != null) {
                    result.add(summaryMessage);
                    log.debug("Injected session summary for conversation: {}", conversationId);
                }
            }

            // Inject episodic memory facts if available
            if (contextWindowProperties.isEpisodicEnabled()) {
                try {
                    String userId = conversationInfo.jobClawUserId();
                    // 无 query 时返回最近的事实
                    String episodicContext = episodicMemory.retrieve(userId, null);
                    if (episodicContext != null && !episodicContext.isBlank()) {
                        result.add(new org.springframework.ai.chat.messages.SystemMessage(episodicContext));
                        log.debug("Injected episodic memory for user: {}", userId);
                    }
                } catch (Exception e) {
                    log.warn("Failed to inject episodic memory for conversation: {}", conversationId, e);
                }
            }

            result.addAll(managedMessages);
            return result;
        } catch (IOException e) {
            throw new RuntimeException("Failed to read conversation: " + conversationId, e);
        }
    }

    /**
     * Load all messages from file without applying window management.
     * Used internally for appending messages to preserve full history.
     *
     * @param conversationId conversation ID
     * @return all messages without truncation
     */
    private List<Message> loadAllMessages(String conversationId) {
        Path file = resolveFile(conversationId);
        if (!Files.exists(file)) return List.of();
        try {
            YamlDocument doc = YamlParser.parse(Files.readString(file));
            return ChatYamlSerializer.deserialize(doc.body());
        } catch (IOException e) {
            throw new RuntimeException("Failed to read conversation: " + conversationId, e);
        }
    }

    @Override
    public void appendAll(String conversationId, List<Message> messages) {
        // Load ALL messages (without window truncation) to preserve full history
        List<Message> existing = loadAllMessages(conversationId);
        List<Message> combined = Stream.concat(existing.stream(), messages.stream()).toList();
        saveAll(conversationId, combined);
    }

    @Override
    public void saveAll(String conversationId, List<Message> messages) {
        UserConversationInfo conversation = UserConversationInfo.parse(conversationId);
        Path file = resolveFile(conversationId);
        FileUtils.ensureDirectory(file.getParent());

        // Preserve createdAt from any existing file; set it fresh on first write
        String createdAt = Instant.now().toString();
        if (Files.exists(file)) {
            try {
                Map<String, String> existing = YamlParser.parse(Files.readString(file)).frontmatter();
                if (existing.containsKey("createdAt")) createdAt = existing.get("createdAt");
            } catch (IOException ignored) {
            }
        }

        Map<String, String> frontmatter = new LinkedHashMap<>();
        frontmatter.put("createdAt", createdAt);
        frontmatter.put("updatedAt", Instant.now().toString());

        String body = ChatYamlSerializer.serialize(messages);
        String content = YamlParser.serialize(new YamlDocument(frontmatter, body));
        try {
            Files.writeString(file, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            log.error("Failed to save conversation: " + conversationId, e);
            throw new RuntimeException("Failed to save conversation: " + conversationId, e);
        }

        sessionSummarizer.autoAsyncSummarize(conversation, messages, (s) -> {
            // fixme 需要通知用户发生了会话压缩
            var toSave = YamlParser.serialize(new YamlDocument(frontmatter, ChatYamlSerializer.serialize(s)));
            try {
                Files.writeString(file, toSave, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            } catch (IOException e) {
                throw new RuntimeException("Failed to save conversation: " + conversationId, e);
            }
        });
        // Async: Update user identity profile
        identityAgent.asyncUpdateUserIdentityAsync(conversation, messages);
        episodicMemory.asyncRecord(conversation, messages);
    }


    @Override
    public void deleteByConversationId(String conversationId) {
        try {
            Files.deleteIfExists(resolveFile(conversationId));

            // Delete archived messages if archive repository is available
            if (archiveRepository != null) {
                try {
                    UserConversationInfo conversationInfo = UserConversationInfo.parse(conversationId);
                    archiveRepository.deleteAllArchives(conversationInfo);
                    log.info("Deleted archives for conversation: {}", conversationId);
                } catch (Exception e) {
                    log.warn("Failed to delete archives for conversation: {}", conversationId, e);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to delete conversation: " + conversationId, e);
        }
    }

    /**
     * Maps a conversation ID (channel name) to
     * {@code conversations/{jobClawUserId}/chat-{channel}.yaml}.
     */
    private Path resolveFile(String conversationId) {
        // 按照约定，原始的conversationId 是按照 jobClawUerId-ConversationId 的格式进行组装的，所以我们首先进行解析，将会话的JobClawUserId依然保存，用于用户会话的隔离
        UserConversationInfo userConversation = UserConversationInfo.parse(conversationId);
        // 为了避免会话的字符串格式异常，我们统一进行md5操作
        String md5 = MD5Utils.md5(userConversation.conversationId());
        return conversationsDir.resolve(userConversation.jobClawUserId()).resolve("chat-" + userConversation.channel() + "-" + md5 + ".yaml");
    }
}
