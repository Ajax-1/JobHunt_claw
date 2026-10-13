package com.git.hui.jobclaw.agents.identity.init;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages unified collection states for all users.
 *
 * <p>Provides centralized state management for the identity initialization process,
 * supporting concurrent access and automatic cleanup.
 *
 * AIDEV-NOTE: State manager for Phase 1 implementation
 */
@Component
public class UnifiedCollectionStateManager {

    private static final Logger log = LoggerFactory.getLogger(UnifiedCollectionStateManager.class);

    // Store unified collection states per user
    private final Map<String, UnifiedCollectionState> states = new ConcurrentHashMap<>();

    /**
     * Get or create a unified collection state for a user.
     *
     * @param jobClawUserId user ID
     * @return the unified collection state
     */
    public UnifiedCollectionState getOrCreateState(String jobClawUserId) {
        return states.computeIfAbsent(jobClawUserId, id -> {
            log.info("Created new unified collection state for user: {}", id);
            return new UnifiedCollectionState(id);
        });
    }

    /**
     * Get the unified collection state for a user.
     *
     * @param jobClawUserId user ID
     * @return the state if exists
     */
    public Optional<UnifiedCollectionState> getState(String jobClawUserId) {
        return Optional.ofNullable(states.get(jobClawUserId));
    }

    /**
     * Check if a user has an active collection state.
     *
     * @param jobClawUserId user ID
     * @return true if state exists and is in progress
     */
    public boolean hasActiveState(String jobClawUserId) {
        UnifiedCollectionState state = states.get(jobClawUserId);
        return state != null && state.isInProgress();
    }

    /**
     * Remove the state for a user (cleanup after completion).
     *
     * @param jobClawUserId user ID
     */
    public void removeState(String jobClawUserId) {
        UnifiedCollectionState removed = states.remove(jobClawUserId);
        if (removed != null) {
            log.debug("Removed unified collection state for user: {}", jobClawUserId);
        }
    }

    /**
     * Get the current initialization phase for a user.
     *
     * @param jobClawUserId user ID
     * @return the current phase, or null if no state exists
     */
    public UnifiedCollectionState.InitializationPhase getCurrentPhase(String jobClawUserId) {
        UnifiedCollectionState state = states.get(jobClawUserId);
        return state != null ? state.getCurrentPhase() : null;
    }

    /**
     * Get the count of active initializations.
     *
     * @return count of users with in-progress states
     */
    public int getActiveCount() {
        return (int) states.values().stream()
                .filter(UnifiedCollectionState::isInProgress)
                .count();
    }

    /**
     * Get all active states (for monitoring/debugging).
     *
     * @return map of user ID to state
     */
    public Map<String, UnifiedCollectionState> getAllActiveStates() {
        return states.entrySet().stream()
                .filter(entry -> entry.getValue().isInProgress())
                .collect(ConcurrentHashMap::new,
                        (map, entry) -> map.put(entry.getKey(), entry.getValue()),
                        ConcurrentHashMap::putAll);
    }

    /**
     * Clear all states (for testing or reset).
     */
    public void clearAll() {
        int count = states.size();
        states.clear();
        log.info("Cleared all unified collection states ({} states removed)", count);
    }
}
