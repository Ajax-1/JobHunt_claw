package com.git.hui.jobclaw.agents.identity.soul;

import com.git.hui.jobclaw.core.agent.memory.ContextWindowProperties;
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
import java.util.List;

/**
 * Manages user-level agent soul profile files (soul.md).
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Read agent soul from workspace/users/{jobClawUserId}/soul.md</li>
 *   <li>Save updated soul to the same location</li>
 *   <li>Create directories if they don't exist</li>
 *   <li>Handle missing files gracefully</li>
 *   <li>Determine when to auto-update soul based on conversation</li>
 * </ul>
 *
 * AIDEV-NOTE: User-level soul.md manager for Phase 1 implementation
 */
@Component
public class UserAgentSoulManager {

    private static final Logger log = LoggerFactory.getLogger(UserAgentSoulManager.class);

    private final Path usersDir;
    private final ContextWindowProperties contextWindowProperties;

    public UserAgentSoulManager(@Value("${agent.workspace:Unknown}") Resource workspaceDir,
                                ContextWindowProperties contextWindowProperties) throws IOException {
        this.usersDir = workspaceDir.getFile().toPath().resolve("users");
        this.contextWindowProperties = contextWindowProperties;
        log.info("AgentSoulManager initialized with users dir: {}", this.usersDir);
    }

    /**
     * Load agent soul from file.
     *
     * @param jobClawUserId user ID
     * @return soul markdown content, or empty string if not exists
     */
    public String loadSoul(String jobClawUserId) {
        Path soulFile = resolveSoulFile(jobClawUserId);
        if (!Files.exists(soulFile)) {
            log.debug("No soul file found for user: {}", jobClawUserId);
            return "";
        }

        try {
            String soul = Files.readString(soulFile);
            log.debug("Loaded soul for user: {} ({} chars)", jobClawUserId, soul.length());
            return soul;
        } catch (IOException e) {
            log.error("Failed to load soul for user: {}", jobClawUserId, e);
            return "";
        }
    }

    /**
     * Save agent soul to file.
     *
     * @param jobClawUserId user ID
     * @param soul soul markdown content
     */
    public void saveSoul(String jobClawUserId, String soul) {
        if (soul == null || soul.isBlank()) {
            log.debug("Skipping save of empty soul for user: {}", jobClawUserId);
            return;
        }

        Path soulFile = resolveSoulFile(jobClawUserId);
        ensureDirectory(soulFile.getParent());

        try {
            Files.writeString(soulFile, soul, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            log.info("Saved soul for user: {} ({} chars) to {}", jobClawUserId, soul.length(), soulFile);
        } catch (IOException e) {
            log.error("Failed to save soul for user: {}", jobClawUserId, e);
            throw new RuntimeException("Failed to save soul for user: " + jobClawUserId, e);
        }
    }

    /**
     * Check if user has a soul file.
     *
     * @param jobClawUserId user ID
     * @return true if soul file exists
     */
    public boolean hasSoul(String jobClawUserId) {
        Path soulFile = resolveSoulFile(jobClawUserId);
        return Files.exists(soulFile);
    }

    /**
     * Delete user soul file.
     *
     * @param jobClawUserId user ID
     */
    public void deleteSoul(String jobClawUserId) {
        Path soulFile = resolveSoulFile(jobClawUserId);
        try {
            Files.deleteIfExists(soulFile);
            log.info("Deleted soul for user: {}", jobClawUserId);
        } catch (IOException e) {
            log.error("Failed to delete soul for user: {}", jobClawUserId, e);
            throw new RuntimeException("Failed to delete soul for user: " + jobClawUserId, e);
        }
    }

    /**
     * Determine if soul should be auto-updated based on conversation.
     *
     * @param jobClawUserId user ID
     * @param messages conversation messages
     * @return true if should update
     */
    public boolean shouldAutoUpdate(String jobClawUserId, List<Message> messages) {
        if (!contextWindowProperties.isSoulAutoUpdate()) {
            return false;
        }

        if ("Unknown".equals(jobClawUserId)) {
            log.debug("Skipping soul update for unknown user");
            return false;
        }

        // Skip if user has no soul yet (should use active collection instead)
        if (!hasSoul(jobClawUserId)) {
            log.debug("Skipping incremental update - user {} has no soul (use active collection)", jobClawUserId);
            return false;
        }

        // Trigger when message count is a multiple of interval
        int frequency = Math.max(contextWindowProperties.getSoulUpdateFrequency(), 1);
        boolean shouldUpdate = messages.size() >= 10 && messages.size() % frequency == 0;
        
        if (shouldUpdate) {
            log.debug("Triggering soul auto-update for user: {} (message count: {}, frequency: {})", 
                     jobClawUserId, messages.size(), frequency);
        }
        
        return shouldUpdate;
    }

    /**
     * Resolve soul file path: workspace/users/{jobClawUserId}/soul.md
     */
    private Path resolveSoulFile(String jobClawUserId) {
        return usersDir.resolve(jobClawUserId).resolve("soul.md");
    }

    private static Path ensureDirectory(Path dir) {
        try {
            Files.createDirectories(dir);
            return dir;
        } catch (IOException e) {
            throw new RuntimeException("Failed to create directory: " + dir, e);
        }
    }
}
