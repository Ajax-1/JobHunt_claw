package com.git.hui.jobclaw.agents.identity.info;

import com.git.hui.jobclaw.agents.identity.init.CollectionState;
import com.git.hui.jobclaw.agents.identity.init.InfoCollector;
import com.git.hui.jobclaw.agents.identity.soul.UserAgentSoulManager;
import com.git.hui.jobclaw.agents.identity.user.UserIdentityManager;
import com.git.hui.jobclaw.core.agent.models.UserConversationInfo;
import com.git.hui.jobclaw.core.preference.AiUserPreferenceProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Auto-generated info collector.
 *
 * <p>This collector automatically generates info.md based on existing soul.md and user.md.
 * No user interaction is required - it runs asynchronously in the background.
 *
 * <p>Workflow:
 * <ol>
 *   <li>Check if soul.md and user.md both exist</li>
 *   <li>Call InfoExtractor to generate info.md</li>
 *   <li>Save info.md asynchronously</li>
 *   <li>Mark collection as completed</li>
 * </ol>
 *
 * AIDEV-NOTE: Auto-generated info collector for Phase 3 implementation
 */
@Component
public class UserAgentInfoCollector implements InfoCollector {

    private static final Logger log = LoggerFactory.getLogger(UserAgentInfoCollector.class);

    private final UserAgentInfoManager infoManager;
    private final UserAgentSoulManager soulManager;
    private final UserIdentityManager userIdentityManager;
    private final UserAgentInfoExtractor infoExtractor;

    // Track collection states per user
    private final ConcurrentHashMap<String, CollectionState> collectionStates = new ConcurrentHashMap<>();

    public UserAgentInfoCollector(UserAgentInfoManager infoManager,
                                  UserAgentSoulManager soulManager,
                                  UserIdentityManager userIdentityManager,
                                  UserAgentInfoExtractor infoExtractor) {
        this.infoManager = infoManager;
        this.soulManager = soulManager;
        this.userIdentityManager = userIdentityManager;
        this.infoExtractor = infoExtractor;

        log.info("InfoCollector initialized");
    }

    @Override
    public AiUserPreferenceProperties.CollectorType getCollectorType() {
        // Info generation is automatic, doesn't fit rule-based or AI-based
        // We use AI_BASED as it uses AI extraction internally
        return AiUserPreferenceProperties.CollectorType.AI_BASED;
    }

    @Override
    public boolean shouldInitiateCollection(String jobClawUserId) {
        // Skip if already has info
        if (infoManager.hasInfo(jobClawUserId)) {
            return false;
        }

        // Only initiate if both soul.md and user.md exist
        if (!soulManager.hasSoul(jobClawUserId)) {
            log.debug("[Info] Skipping - soul.md not ready for user: {}", jobClawUserId);
            return false;
        }

        if (!userIdentityManager.hasIdentity(jobClawUserId)) {
            log.debug("[Info] Skipping - user.md not ready for user: {}", jobClawUserId);
            return false;
        }

        // Skip if collection already in progress
        CollectionState state = collectionStates.get(jobClawUserId);
        if (state != null && state.isInProgress()) {
            return false;
        }

        return true;
    }

    @Override
    public void initiateCollection(UserConversationInfo userConversationInfo) {
        String jobClawUserId = userConversationInfo.jobClawUserId();
        if (!shouldInitiateCollection(jobClawUserId)) {
            log.debug("[Info] Skipping collection initiation for user: {}", jobClawUserId);
            return;
        }

        log.info("[Info] Initiating auto-generation of info.md for user: {}", jobClawUserId);

        // Create collection state
        String channel = userConversationInfo.channel();
        String conversationId = userConversationInfo.conversationId();
        CollectionState state = new CollectionState(jobClawUserId);
        state.start(channel, conversationId);
        collectionStates.put(jobClawUserId, state);

        // Start async generation
        generateInfoAsync(state);
    }

    @Override
    public void processAnswer(UserConversationInfo userConversationInfo, String userMessage, Runnable completeCallback) {
        // Info collector doesn't need user interaction
        // This method should not be called, but we handle it gracefully
        log.debug("[Info] processAnswer called but info collector doesn't require user interaction");
    }

    @Override
    public Optional<CollectionState> getCollectionState(String jobClawUserId) {
        return Optional.ofNullable(collectionStates.get(jobClawUserId));
    }

    /**
     * Generate info.md asynchronously
     */
    private void generateInfoAsync(CollectionState state) {
        String jobClawUserId = state.getJobClawUserId();

        try {
            // Load soul.md and user.md
            String soulMd = soulManager.loadSoul(jobClawUserId);
            String userMd = userIdentityManager.loadIdentity(jobClawUserId);

            log.info("[Info] Starting async info.md generation for user: {}", jobClawUserId);

            // Extract info asynchronously
            infoExtractor.extractAsync(buildUserConversationInfo(state), "", userMd, soulMd)
                    .thenAccept(infoMd -> {
                        if (infoMd != null && !infoMd.isBlank()) {
                            // Save info.md
                            infoManager.saveInfo(jobClawUserId, infoMd);
                            log.info("[Info] info.md saved successfully for user: {}", jobClawUserId);

                            // Mark state as completed
                            state.complete();
                            collectionStates.remove(jobClawUserId); // Clean up state
                        } else {
                            log.warn("[Info] Generated info.md is empty for user: {}", jobClawUserId);
                            state.complete();
                            collectionStates.remove(jobClawUserId);
                        }
                    })
                    .exceptionally(ex -> {
                        log.error("[Info] Failed to generate info.md for user: {}", jobClawUserId, ex);
                        // Mark as completed anyway to avoid blocking
                        state.complete();
                        collectionStates.remove(jobClawUserId);
                        return null;
                    });

        } catch (Exception e) {
            log.error("[Info] Error initiating info.md generation for user: {}", jobClawUserId, e);
            state.complete();
            collectionStates.remove(jobClawUserId);
        }
    }
}
