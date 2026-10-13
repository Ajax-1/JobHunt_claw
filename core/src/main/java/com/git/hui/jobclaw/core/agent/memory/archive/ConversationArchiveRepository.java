package com.git.hui.jobclaw.core.agent.memory.archive;

import com.git.hui.jobclaw.core.agent.memory.ChatYamlSerializer;
import com.git.hui.jobclaw.core.agent.models.UserConversationInfo;
import com.git.hui.jobclaw.core.utils.FileUtils;
import com.git.hui.jobclaw.core.utils.MD5Utils;
import com.git.hui.jobclaw.core.utils.files.YamlDocument;
import com.git.hui.jobclaw.core.utils.files.YamlParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 对话历史归档仓库
 * <p>
 * 负责存储和管理被 SmartWindowChatMemory 压缩后丢弃的对话内容，
 * 避免对话历史数据丢失。
 * <p>
 * 归档文件结构：
 * {workspace}/archives/{jobClawUserId}/{channel}/{timestamp}-{conversationId}-{chunkIndex}.yaml
 * <p>
 * 每个归档文件包含：
 * - frontmatter: 元数据（归档时间、会话ID、消息数量、摘要等）
 * - body: 完整的对话消息列表（YAML格式）
 *
 * @date 2026/6/5
 */
@Component
public class ConversationArchiveRepository {

    private static final Logger log = LoggerFactory.getLogger(ConversationArchiveRepository.class);
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final Path archivesDir;
    private final ArchiveProperties properties;

    public ConversationArchiveRepository(
            @Value("${agent.workspace:Unknown}") Resource workspaceDir,
            ArchiveProperties properties) throws IOException {
        this.archivesDir = workspaceDir.getFile().toPath().resolve("archives");
        this.properties = properties;
        log.info("ConversationArchiveRepository initialized with archives directory: {}, enabled: {}",
                archivesDir, properties.isEnabled());
    }

    /**
     * 归档一批对话消息
     *
     * @param conversationInfo 会话信息
     * @param messages         要归档的消息列表
     * @param summary          可选的摘要（如果这些消息已被压缩）
     * @return 归档文件路径
     */
    public CompletableFuture<Path> archiveAsync(UserConversationInfo conversationInfo,
                                                  List<Message> messages,
                                                  String summary) {
        if (!properties.isEnabled()) {
            log.debug("Archive is disabled, skipping archive for conversation: {}", conversationInfo);
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                return archive(conversationInfo, messages, summary);
            } catch (Exception e) {
                log.error("Failed to archive conversation: {}", conversationInfo, e);
                throw new RuntimeException("Archive failed", e);
            }
        });
    }

    /**
     * 同步归档一批对话消息
     *
     * @param conversationInfo 会话信息
     * @param messages         要归档的消息列表
     * @param summary          可选的摘要（如果这些消息已被压缩）
     * @return 归档文件路径
     */
    public Path archive(UserConversationInfo conversationInfo,
                        List<Message> messages,
                        String summary) {
        if (!properties.isEnabled()) {
            log.debug("Archive is disabled, skipping archive for conversation: {}", conversationInfo);
            return null;
        }

        if (messages == null || messages.isEmpty()) {
            log.warn("No messages to archive for conversation: {}", conversationInfo);
            return null;
        }

        try {
            // 生成归档文件路径
            String timestamp = Instant.now().atZone(java.time.ZoneId.systemDefault())
                    .format(TIMESTAMP_FORMATTER);
            String conversationIdHash = MD5Utils.md5(conversationInfo.conversationId());
            String fileName = String.format("%s-%s-%03d.yaml",
                    timestamp,
                    conversationIdHash,
                    getNextChunkIndex(conversationInfo));

            Path file = resolveArchiveFile(conversationInfo, fileName);
            FileUtils.ensureDirectory(file.getParent());

            // 构建归档元数据
            Map<String, String> frontmatter = new LinkedHashMap<>();
            frontmatter.put("archivedAt", Instant.now().toString());
            frontmatter.put("jobClawUserId", conversationInfo.jobClawUserId());
            frontmatter.put("channel", conversationInfo.channel());
            frontmatter.put("conversationId", conversationInfo.conversationId());
            frontmatter.put("messageCount", String.valueOf(messages.size()));
            frontmatter.put("summary", summary != null ? summary : "");

            // 序列化消息
            String body = ChatYamlSerializer.serialize(messages);
            String content = YamlParser.serialize(new YamlDocument(frontmatter, body));

            // 写入文件
            Files.writeString(file, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            log.info("Archived {} messages for conversation {} to: {}",
                    messages.size(), conversationInfo, file.getFileName());

            return file;

        } catch (Exception e) {
            log.error("Failed to archive conversation: {}", conversationInfo, e);
            throw new RuntimeException("Archive failed", e);
        }
    }

    /**
     * 检索指定会话的所有归档消息
     *
     * @param conversationInfo 会话信息
     * @return 所有归档的消息列表（按时间顺序）
     */
    public List<Message> retrieveAllArchives(UserConversationInfo conversationInfo) {
        Path archiveDir = resolveArchiveDirectory(conversationInfo);
        if (!Files.exists(archiveDir)) {
            return List.of();
        }

        try (Stream<Path> files = Files.list(archiveDir)) {
            return files
                    .filter(p -> p.toString().endsWith(".yaml"))
                    .sorted()
                    .flatMap(p -> {
                        try {
                            YamlDocument doc = YamlParser.parse(Files.readString(p));
                            return ChatYamlSerializer.deserialize(doc.body()).stream();
                        } catch (IOException e) {
                            log.warn("Failed to read archive file: {}", p, e);
                            return Stream.empty();
                        }
                    })
                    .collect(Collectors.toList());
        } catch (IOException e) {
            log.error("Failed to retrieve archives for conversation: {}", conversationInfo, e);
            return List.of();
        }
    }

    /**
     * 检索指定会话的归档摘要列表
     *
     * @param conversationInfo 会话信息
     * @return 归档摘要列表（按时间顺序）
     */
    public List<ArchiveSummary> listArchiveSummaries(UserConversationInfo conversationInfo) {
        Path archiveDir = resolveArchiveDirectory(conversationInfo);
        if (!Files.exists(archiveDir)) {
            return List.of();
        }

        try (Stream<Path> files = Files.list(archiveDir)) {
            return files
                    .filter(p -> p.toString().endsWith(".yaml"))
                    .sorted()
                    .map(p -> {
                        try {
                            YamlDocument doc = YamlParser.parse(Files.readString(p));
                            Map<String, String> fm = doc.frontmatter();
                            return new ArchiveSummary(
                                    fm.get("archivedAt"),
                                    Integer.parseInt(fm.getOrDefault("messageCount", "0")),
                                    fm.getOrDefault("summary", ""),
                                    p.getFileName().toString()
                            );
                        } catch (IOException e) {
                            log.warn("Failed to read archive file: {}", p, e);
                            return null;
                        }
                    })
                    .filter(java.util.Objects::nonNull)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            log.error("Failed to list archive summaries for conversation: {}", conversationInfo, e);
            return List.of();
        }
    }

    /**
     * 删除指定会话的所有归档
     *
     * @param conversationInfo 会话信息
     */
    public void deleteAllArchives(UserConversationInfo conversationInfo) {
        Path archiveDir = resolveArchiveDirectory(conversationInfo);
        if (!Files.exists(archiveDir)) {
            return;
        }

        try {
            Files.walk(archiveDir)
                    .filter(Files::isRegularFile)
                    .forEach(p -> {
                        try {
                            Files.delete(p);
                            log.debug("Deleted archive file: {}", p.getFileName());
                        } catch (IOException e) {
                            log.warn("Failed to delete archive file: {}", p, e);
                        }
                    });
            log.info("Deleted all archives for conversation: {}", conversationInfo);
        } catch (IOException e) {
            log.error("Failed to delete archives for conversation: {}", conversationInfo, e);
        }
    }

    /**
     * 获取下一个归档块索引
     */
    private int getNextChunkIndex(UserConversationInfo conversationInfo) {
        Path archiveDir = resolveArchiveDirectory(conversationInfo);
        if (!Files.exists(archiveDir)) {
            return 0;
        }

        try (Stream<Path> files = Files.list(archiveDir)) {
            return files
                    .filter(p -> p.toString().endsWith(".yaml"))
                    .mapToInt(p -> {
                        String fileName = p.getFileName().toString();
                        // 文件名格式: timestamp-conversationIdHash-chunkIndex.yaml
                        String[] parts = fileName.replace(".yaml", "").split("-");
                        if (parts.length >= 3) {
                            try {
                                return Integer.parseInt(parts[2]);
                            } catch (NumberFormatException e) {
                                return -1;
                            }
                        }
                        return -1;
                    })
                    .max()
                    .orElse(-1) + 1;
        } catch (IOException e) {
            log.warn("Failed to determine next chunk index for: {}", conversationInfo, e);
            return 0;
        }
    }

    /**
     * 解析归档目录路径
     */
    private Path resolveArchiveDirectory(UserConversationInfo conversationInfo) {
        return archivesDir
                .resolve(conversationInfo.jobClawUserId())
                .resolve(conversationInfo.channel());
    }

    /**
     * 解析归档文件路径
     */
    private Path resolveArchiveFile(UserConversationInfo conversationInfo, String fileName) {
        return resolveArchiveDirectory(conversationInfo).resolve(fileName);
    }

    /**
     * 归档摘要信息
     */
    public record ArchiveSummary(
            String archivedAt,      // 归档时间
            int messageCount,       // 消息数量
            String summary,         // 摘要
            String fileName         // 文件名
    ) {
    }
}
