package com.git.hui.jobclaw.agents.identity.init;

import com.git.hui.jobclaw.core.agent.models.UserConversationInfo;
import com.git.hui.jobclaw.core.preference.AiUserPreferenceProperties;

import java.util.Optional;

/**
 * Common interface for identity/profile collectors.
 *
 * <p>Implementations can use different strategies:
 * <ul>
 *   <li>Rule-based: Predefined question flow</li>
 *   <li>AI-based: LLM-driven dynamic conversation</li>
 * </ul>
 *
 * AIDEV-NOTE: Common interface for Phase 3 identity collection strategies
 */
public interface InfoCollector {


    /**
     * Check if we should initiate identity collection for this user.
     *
     * @param jobClawUserId user ID
     * @return true if collection should be initiated
     */
    boolean shouldInitiateCollection(String jobClawUserId);

    /**
     * Initiate active identity collection for a new user.
     *
     * @param userConversationInfo user conversation info
     */
    void initiateCollection(UserConversationInfo userConversationInfo);

    /**
     * Process user's answer and continue collection.
     *
     * @param userConversationInfo user conversation info
     * @param userMessage user's message
     */
    void processAnswer(UserConversationInfo userConversationInfo, String userMessage, Runnable completeCallback);

    /**
     * Get collection state for a user.
     *
     * @param jobClawUserId user ID
     * @return collection state if exists
     */
    Optional<CollectionState> getCollectionState(String jobClawUserId);

    /**
     * Get the collector type.
     *
     * @return collector type
     */
    AiUserPreferenceProperties.CollectorType getCollectorType();

    default UserConversationInfo buildUserConversationInfo(CollectionState state) {
        return new UserConversationInfo(state.getJobClawUserId(),
                state.getActiveChannel(),
                state.getConversationId(),
                false);
    }
}
