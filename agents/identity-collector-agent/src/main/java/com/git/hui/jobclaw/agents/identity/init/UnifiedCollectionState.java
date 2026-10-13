package com.git.hui.jobclaw.agents.identity.init;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Unified state manager for the complete identity initialization sequence.
 *
 * <p>Coordinates three initialization phases:
 * <ol>
 *   <li>SOUL_COLLECTION - AI personality collection via multi-turn conversation</li>
 *   <li>USER_ID_COLLECTION - User profile collection via multi-turn conversation</li>
 *   <li>INFO_GENERATION - AI identity card auto-generation (no user interaction)</li>
 * </ol>
 *
 * <p>Each phase uses its own IdentityCollectionState for detailed tracking.
 *
 * AIDEV-NOTE: Unified state for Phase 1 implementation - replaces AgentIdentityInitializer
 */
public class UnifiedCollectionState {

    /**
     * Initialization phase enum
     */
    public enum InitializationPhase {
        /** Collecting soul.md (AI personality) */
        SOUL_COLLECTION,
        /** Collecting user.md (user profile) */
        USER_ID_COLLECTION,
        /** Generating info.md (AI identity card) */
        INFO_GENERATION,
        /** All phases completed */
        COMPLETED
    }

    /**
     * Overall status enum
     */
    public enum OverallStatus {
        /** Not started */
        NOT_STARTED,
        /** In progress - one of the phases is active */
        IN_PROGRESS,
        /** All phases completed successfully */
        COMPLETED,
        /** User abandoned the process */
        ABANDONED,
        /** Error occurred during initialization */
        ERROR
    }

    // User identification
    private final String jobClawUserId;

    // Phase tracking
    private final AtomicReference<InitializationPhase> currentPhase;
    private final AtomicReference<OverallStatus> overallStatus;

    // Individual phase states
    private CollectionState soulState;
    private CollectionState userIdentityState;
    private CollectionState infoState;

    // Timestamps
    private final Instant startedAt;
    private Instant completedAt;
    private Instant lastUpdatedAt;

    public UnifiedCollectionState(String jobClawUserId) {
        this.jobClawUserId = jobClawUserId;
        this.currentPhase = new AtomicReference<>(InitializationPhase.SOUL_COLLECTION);
        this.overallStatus = new AtomicReference<>(OverallStatus.NOT_STARTED);
        this.startedAt = Instant.now();
        this.lastUpdatedAt = Instant.now();
    }

    /**
     * Start the initialization process
     */
    public void start() {
        this.overallStatus.set(OverallStatus.IN_PROGRESS);
        this.lastUpdatedAt = Instant.now();
    }

    /**
     * Advance to the next phase
     *
     * @return the new phase, or null if already completed
     */
    public InitializationPhase advanceToNextPhase() {
        InitializationPhase current = currentPhase.get();
        InitializationPhase next = switch (current) {
            case SOUL_COLLECTION -> InitializationPhase.USER_ID_COLLECTION;
            case USER_ID_COLLECTION -> InitializationPhase.INFO_GENERATION;
            case INFO_GENERATION -> InitializationPhase.COMPLETED;
            case COMPLETED -> null;
        };

        if (next != null) {
            currentPhase.set(next);
            lastUpdatedAt = Instant.now();

            if (next == InitializationPhase.COMPLETED) {
                complete();
            }
        }

        return next;
    }

    /**
     * Mark current phase as completed
     */
    public void markCurrentPhaseCompleted() {
        lastUpdatedAt = Instant.now();
    }

    /**
     * Mark entire initialization as completed
     */
    public void complete() {
        this.currentPhase.set(InitializationPhase.COMPLETED);
        this.overallStatus.set(OverallStatus.COMPLETED);
        this.completedAt = Instant.now();
        this.lastUpdatedAt = Instant.now();
    }

    /**
     * Mark initialization as abandoned
     */
    public void abandon() {
        this.overallStatus.set(OverallStatus.ABANDONED);
        this.lastUpdatedAt = Instant.now();
    }

    /**
     * Mark initialization as error
     */
    public void error() {
        this.overallStatus.set(OverallStatus.ERROR);
        this.lastUpdatedAt = Instant.now();
    }

    /**
     * Check if initialization is in progress
     */
    public boolean isInProgress() {
        return overallStatus.get() == OverallStatus.IN_PROGRESS;
    }

    /**
     * Check if initialization is completed
     */
    public boolean isCompleted() {
        return overallStatus.get() == OverallStatus.COMPLETED;
    }

    /**
     * Set the soul collection state
     */
    public void setSoulState(CollectionState soulState) {
        this.soulState = soulState;
        this.lastUpdatedAt = Instant.now();
    }

    /**
     * Set the user identity collection state
     */
    public void setUserIdentityState(CollectionState userIdentityState) {
        this.userIdentityState = userIdentityState;
        this.lastUpdatedAt = Instant.now();
    }

    /**
     * Set the info generation state
     */
    public void setInfoState(CollectionState infoState) {
        this.infoState = infoState;
        this.lastUpdatedAt = Instant.now();
    }

    // Getters

    public String getJobClawUserId() {
        return jobClawUserId;
    }

    public InitializationPhase getCurrentPhase() {
        return currentPhase.get();
    }

    public OverallStatus getOverallStatus() {
        return overallStatus.get();
    }

    public CollectionState getSoulState() {
        return soulState;
    }

    public CollectionState getUserIdentityState() {
        return userIdentityState;
    }

    public CollectionState getInfoState() {
        return infoState;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public Instant getLastUpdatedAt() {
        return lastUpdatedAt;
    }

    @Override
    public String toString() {
        return "UnifiedCollectionState{" +
                "jobClawUserId='" + jobClawUserId + '\'' +
                ", currentPhase=" + currentPhase.get() +
                ", overallStatus=" + overallStatus.get() +
                ", hasSoulState=" + (soulState != null) +
                ", hasUserIdentityState=" + (userIdentityState != null) +
                ", hasInfoState=" + (infoState != null) +
                '}';
    }
}
