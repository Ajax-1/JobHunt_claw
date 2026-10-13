package com.git.hui.jobclaw.agents.identity.info;

import com.git.hui.jobclaw.agents.identity.user.UserIdentityManager;
import com.git.hui.jobclaw.core.agent.memory.ContextWindowProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Manages user-level agent identity card files (info.md).
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Read agent info from workspace/users/{jobClawUserId}/info.md</li>
 *   <li>Save updated info to the same location</li>
 *   <li>Create directories if they don't exist</li>
 *   <li>Handle missing files gracefully</li>
 *   <li>Determine when to update info based on user identity changes</li>
 * </ul>
 *
 * AIDEV-NOTE: User-level info.md manager for Phase 1 implementation
 */
@Component
public class UserAgentInfoManager {

    private static final Logger log = LoggerFactory.getLogger(UserAgentInfoManager.class);

    private final Path usersDir;
    private final UserIdentityManager userIdentityManager;
    private final ContextWindowProperties contextWindowProperties;

    public UserAgentInfoManager(@Value("${agent.workspace:Unknown}") Resource workspaceDir,
                                UserIdentityManager userIdentityManager,
                                ContextWindowProperties contextWindowProperties) throws IOException {
        this.usersDir = workspaceDir.getFile().toPath().resolve("users");
        this.userIdentityManager = userIdentityManager;
        this.contextWindowProperties = contextWindowProperties;
        log.info("AgentInfoManager initialized with users dir: {}", this.usersDir);
    }

    /**
     * Load agent info from file.
     *
     * @param jobClawUserId user ID
     * @return info markdown content, or empty string if not exists
     */
    public String loadInfo(String jobClawUserId) {
        Path infoFile = resolveInfoFile(jobClawUserId);
        if (!Files.exists(infoFile)) {
            log.debug("No info file found for user: {}", jobClawUserId);
            return "";
        }

        try {
            String info = Files.readString(infoFile);
            log.debug("Loaded info for user: {} ({} chars)", jobClawUserId, info.length());
            return info;
        } catch (IOException e) {
            log.error("Failed to load info for user: {}", jobClawUserId, e);
            return "";
        }
    }

    /**
     * Save agent info to file.
     *
     * @param jobClawUserId user ID
     * @param info info markdown content
     */
    public void saveInfo(String jobClawUserId, String info) {
        if (info == null || info.isBlank()) {
            log.debug("Skipping save of empty info for user: {}", jobClawUserId);
            return;
        }

        Path infoFile = resolveInfoFile(jobClawUserId);
        ensureDirectory(infoFile.getParent());

        try {
            Files.writeString(infoFile, info, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            log.info("Saved info for user: {} ({} chars) to {}", jobClawUserId, info.length(), infoFile);
        } catch (IOException e) {
            log.error("Failed to save info for user: {}", jobClawUserId, e);
            throw new RuntimeException("Failed to save info for user: " + jobClawUserId, e);
        }
    }

    /**
     * Check if user has a info file.
     *
     * @param jobClawUserId user ID
     * @return true if info file exists
     */
    public boolean hasInfo(String jobClawUserId) {
        Path infoFile = resolveInfoFile(jobClawUserId);
        return Files.exists(infoFile);
    }

    /**
     * Delete user info file.
     *
     * @param jobClawUserId user ID
     */
    public void deleteInfo(String jobClawUserId) {
        Path infoFile = resolveInfoFile(jobClawUserId);
        try {
            Files.deleteIfExists(infoFile);
            log.info("Deleted info for user: {}", jobClawUserId);
        } catch (IOException e) {
            log.error("Failed to delete info for user: {}", jobClawUserId, e);
            throw new RuntimeException("Failed to delete info for user: " + jobClawUserId, e);
        }
    }

    /**
     * Determine if info should be updated based on user identity changes.
     *
     * @param jobClawUserId user ID
     * @return true if should update
     */
    public boolean shouldUpdateBasedOnUserIdentity(String jobClawUserId) {
        if (!contextWindowProperties.isInfoAutoUpdate()) {
            return false;
        }

        if ("Unknown".equals(jobClawUserId)) {
            log.debug("Skipping info update for unknown user");
            return false;
        }

        // Skip if user has no info yet (should use initial creation instead)
        if (!hasInfo(jobClawUserId)) {
            log.debug("Skipping incremental update - user {} has no info (use initial creation)", jobClawUserId);
            return false;
        }

        // Check if user has identity (user.md)
        if (!userIdentityManager.hasIdentity(jobClawUserId)) {
            log.debug("User {} has no identity yet, skip info update", jobClawUserId);
            return false;
        }

        // TODO: Implement more sophisticated change detection
        // For now, we'll rely on external triggers (e.g., after user.md is updated)
        log.debug("Info update check for user: {} - returning false (manual trigger recommended)", jobClawUserId);
        return false;
    }

    /**
     * Resolve info file path: workspace/users/{jobClawUserId}/info.md
     */
    private Path resolveInfoFile(String jobClawUserId) {
        return usersDir.resolve(jobClawUserId).resolve("info.md");
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
