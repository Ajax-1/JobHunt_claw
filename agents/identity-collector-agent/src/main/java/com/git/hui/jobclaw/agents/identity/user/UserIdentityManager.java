package com.git.hui.jobclaw.agents.identity.user;

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
 * Manages user identity profile files (user.md).
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Read user identity from workspace/users/{jobClawUserId}/user.md</li>
 *   <li>Save updated identity to the same location</li>
 *   <li>Create directories if they don't exist</li>
 *   <li>Handle missing files gracefully</li>
 * </ul>
 *
 */
@Component
public class UserIdentityManager {

    private static final Logger log = LoggerFactory.getLogger(UserIdentityManager.class);

    private final Path usersDir;

    private final ContextWindowProperties contextWindowProperties;

    public UserIdentityManager(@Value("${agent.workspace:Unknown}") Resource workspaceDir, ContextWindowProperties contextWindowProperties) throws IOException {
        this.usersDir = workspaceDir.getFile().toPath().resolve("users");
        this.contextWindowProperties = contextWindowProperties;
    }

    public boolean shouldAutoUpdateIdentity(String jobClawUserId, List<Message> messages) {
        if (!contextWindowProperties.isIdentityAutoUpdate()) {
            return false;
        }

        if ("Unknown".equals(jobClawUserId)) {
            log.debug("Skipping identity update for unknown user");
            return false;
        }

        // Skip if user has no identity yet (should use active collection instead)
        if (!hasIdentity(jobClawUserId)) {
            log.debug("Skipping incremental update - user {} has no identity (use active collection)", jobClawUserId);
            return false;
        }

        // Trigger when message count is a multiple of interval
        return messages.size() >= 10 && messages.size() % Math.max(contextWindowProperties.getUpdateIdentityFrequency(), 1) == 0;
    }


    /**
     * Load user identity from file.
     *
     * @param jobClawUserId user ID
     * @return identity markdown content, or empty string if not exists
     */
    public String loadIdentity(String jobClawUserId) {
        Path identityFile = resolveIdentityFile(jobClawUserId);
        if (!Files.exists(identityFile)) {
            log.debug("No identity file found for user: {}", jobClawUserId);
            return "";
        }

        try {
            String identity = Files.readString(identityFile);
            log.debug("Loaded identity for user: {} ({} chars)", jobClawUserId, identity.length());
            return identity;
        } catch (IOException e) {
            log.error("Failed to load identity for user: {}", jobClawUserId, e);
            return "";
        }
    }

    /**
     * Save user identity to file.
     *
     * @param jobClawUserId user ID
     * @param identity identity markdown content
     */
    public void saveIdentity(String jobClawUserId, String identity) {
        if (identity == null || identity.isBlank()) {
            log.debug("Skipping save of empty identity for user: {}", jobClawUserId);
            return;
        }

        Path identityFile = resolveIdentityFile(jobClawUserId);
        ensureDirectory(identityFile.getParent());

        try {
            Files.writeString(identityFile, identity, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            log.info("Saved identity for user: {} ({} chars) to {}", jobClawUserId, identity.length(), identityFile);
        } catch (IOException e) {
            log.error("Failed to save identity for user: {}", jobClawUserId, e);
            throw new RuntimeException("Failed to save identity for user: " + jobClawUserId, e);
        }
    }

    /**
     * Check if user has a identity file.
     *
     * @param jobClawUserId user ID
     * @return true if identity file exists
     */
    public boolean hasIdentity(String jobClawUserId) {
        Path identityFile = resolveIdentityFile(jobClawUserId);
        return Files.exists(identityFile);
    }

    /**
     * Delete user identity file.
     *
     * @param jobClawUserId user ID
     */
    public void deleteIdentity(String jobClawUserId) {
        Path identityFile = resolveIdentityFile(jobClawUserId);
        try {
            Files.deleteIfExists(identityFile);
            log.info("Deleted identity for user: {}", jobClawUserId);
        } catch (IOException e) {
            log.error("Failed to delete identity for user: {}", jobClawUserId, e);
            throw new RuntimeException("Failed to delete identity for user: " + jobClawUserId, e);
        }
    }

    /**
     * Resolve identity file path: workspace/users/{jobClawUserId}/user.md
     */
    private Path resolveIdentityFile(String jobClawUserId) {
        return usersDir.resolve(jobClawUserId).resolve("user.md");
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
