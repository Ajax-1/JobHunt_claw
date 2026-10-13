package com.git.hui.jobclaw.agents.identity;

import com.git.hui.jobclaw.agents.identity.global.AgentIdentityManager;
import com.git.hui.jobclaw.agents.identity.info.UserAgentInfoManager;
import com.git.hui.jobclaw.agents.identity.init.UnifiedIdentityInitializer;
import com.git.hui.jobclaw.agents.identity.soul.UserAgentSoulManager;
import com.git.hui.jobclaw.agents.identity.user.UserIdentityExtractor;
import com.git.hui.jobclaw.agents.identity.user.UserIdentityManager;
import com.git.hui.jobclaw.core.agent.IIdentityAgent;
import com.git.hui.jobclaw.core.agent.models.UserConversationInfo;
import io.micrometer.common.util.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

/**
 * 身份Agent
 * @date 2026/4/17
 */
@Slf4j
public class IdentityAgent implements IIdentityAgent {
    private final AgentIdentityManager agentIdentityManager;
    private final UserAgentSoulManager agentSoulManager;
    private final UserAgentInfoManager agentInfoManager;
    private final UserIdentityManager userIdentityManager;

    private final UnifiedIdentityInitializer unifiedInitializer;


    public IdentityAgent(AgentIdentityManager agentIdentityManager,
                         UserAgentSoulManager agentSoulManager,
                         UserAgentInfoManager agentInfoManager,
                         UserIdentityManager userIdentityManager, UnifiedIdentityInitializer unifiedInitializer) {
        this.agentIdentityManager = agentIdentityManager;
        this.agentSoulManager = agentSoulManager;
        this.agentInfoManager = agentInfoManager;
        this.userIdentityManager = userIdentityManager;
        this.unifiedInitializer = unifiedInitializer;
    }


    public boolean triggerToCollectIdentity(UserConversationInfo conversationInfo, String userMessage) {
        // Step 1: Check and advance unified initialization sequence
        // This handles soul.md → user.md → info.md initialization
        if (unifiedInitializer.checkAndAdvance(conversationInfo, userMessage)) {
            log.info("Message handled by unified initializer for user: {}", conversationInfo);
            return true;
        }
        return false;
    }

    @Override
    public String buildSoulPrompt(String jobClawUserId) {
        StringBuilder sb = new StringBuilder();

        // 1. Load global agent.md (operation manual)
        String agentMd = agentIdentityManager.loadAgentIdentity();
        if (agentMd != null && !agentMd.isBlank()) {
            sb.append("## Agent Operation Manual\n");
            sb.append(agentMd);
            sb.append("\n\n");
            log.debug("Injected agent.md for user: {} ({} chars)", jobClawUserId, agentMd.length());
        }

        // 2. Load user-level soul.md (personality)
        String soulMd = agentSoulManager.loadSoul(jobClawUserId);
        if (soulMd != null && !soulMd.isBlank()) {
            sb.append("## Your Soul & Personality\n");
            sb.append(soulMd);
            sb.append("\n\n");
            log.debug("Injected soul.md for user: {} ({} chars)", jobClawUserId, soulMd.length());
        }
        String result = sb.toString();
        if (result.isBlank()) {
            log.debug("No identity documents to inject for user: {}", jobClawUserId);
            return null;
        }

        log.debug("Built system prompt for user: {} ({} chars total)", jobClawUserId, result.length());
        return result;
    }

    /**
     * Build system prompt by injecting identity documents.
     *
     * Priority order: agent.md > soul.md > info.md > user.md
     */
    public String buildSystemPrompt(String jobClawUserId) {
        StringBuilder sb = new StringBuilder();

        // 1. Load global agent.md (operation manual)
        String agentMd = agentIdentityManager.loadAgentIdentity();
        if (agentMd != null && !agentMd.isBlank()) {
            sb.append(agentMd);
            sb.append("\n\n");
            log.debug("Injected agent.md for user: {} ({} chars)", jobClawUserId, agentMd.length());
        }

        // 2. Load user-level soul.md (personality)
        String soulMd = agentSoulManager.loadSoul(jobClawUserId);
        if (soulMd != null && !soulMd.isBlank()) {
            sb.append(soulMd);
            sb.append("\n\n");
            log.debug("Injected soul.md for user: {} ({} chars)", jobClawUserId, soulMd.length());
        }

        // 3. Load user-level info.md (identity card)
        String infoMd = agentInfoManager.loadInfo(jobClawUserId);
        if (infoMd != null && !infoMd.isBlank()) {
            sb.append(infoMd);
            sb.append("\n\n");
            log.debug("Injected info.md for user: {} ({} chars)", jobClawUserId, infoMd.length());
        }

        // 4. Load user profile user.md
        String userMd = userIdentityManager.loadIdentity(jobClawUserId);
        if (userMd != null && !userMd.isBlank()) {
            sb.append(userMd);
            sb.append("\n\n");
            log.debug("Injected user.md for user: {} ({} chars)", jobClawUserId, userMd.length());
        }

        String result = sb.toString();
        if (result.isBlank()) {
            log.debug("No identity documents to inject for user: {}", jobClawUserId);
            return null;
        }

        log.debug("Built system prompt for user: {} ({} chars total)", jobClawUserId, result.length());
        return result;
    }

    @Autowired
    private UserIdentityManager useridentityManager;
    @Autowired
    private UserIdentityExtractor useridentityExtractor;

    /**
     * Asynchronously update user identity profile based on conversation history.
     *
     * <p>This method handles incremental identity updates for existing users.
     * Unlike active collection (for new users), this performs passive extraction
     * from conversation history to keep the identity profile up-to-date.
     *
     * <p>Update strategy:
     * <ul>
     *   <li>Only triggers when conversation reaches certain size (avoid frequent AI calls)</li>
     *   <li>Uses existing identity as baseline for incremental update</li>
     *   <li>Runs asynchronously to avoid blocking conversation</li>
     *   <li>Graceful fallback on failure (keeps existing identity)</li>
     * </ul>
     *
     * @param conversationId conversation ID (contains jobClawUserId)
     * @param messages conversation messages
     */
    public void asyncUpdateUserIdentityAsync(UserConversationInfo conversationInfo, List<Message> messages) {
        if (conversationInfo.group()) {
            return;
        }

        String jobClawUserId = conversationInfo.jobClawUserId();
        String conversationId = conversationInfo.conversationId();
        try {
            // Check if update should be triggered (avoid frequent AI calls)
            if (!useridentityManager.shouldAutoUpdateIdentity(jobClawUserId, messages)) {
                return;
            }

            log.info("Triggering incremental identity update for user: {} ({} messages)", jobClawUserId, messages.size());

            // Load existing identity as baseline
            String currentIdentity = useridentityManager.loadIdentity(jobClawUserId);

            // Extract and update identity asynchronously
            useridentityExtractor.extractAsync(conversationInfo, currentIdentity, messages)
                    .thenAcceptAsync(updatedIdentity -> {
                        if (StringUtils.isNotBlank(updatedIdentity)) {
                            useridentityManager.saveIdentity(jobClawUserId, updatedIdentity);
                            log.info("User identity updated incrementally for: {}", jobClawUserId);
                        } else {
                            log.warn("identity extraction returned empty for user: {}, keeping existing", jobClawUserId);
                        }
                    })
                    .exceptionally(ex -> {
                        log.error("Failed to update user identity incrementally for: {}, keeping existing identity",
                                jobClawUserId, ex);
                        return null;
                    });

        } catch (Exception e) {
            log.error("Failed to trigger incremental identity update for conversation: {}", conversationId, e);
            // Don't throw - identity update is non-critical
        }
    }
}
