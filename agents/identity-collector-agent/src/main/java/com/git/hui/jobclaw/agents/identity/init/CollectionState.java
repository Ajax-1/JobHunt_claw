package com.git.hui.jobclaw.agents.identity.init;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the state of active identity collection conversations.
 *
 * <p>Tracks which users are in the middle of identity collection,
 * what questions have been asked, and what information is still needed.
 *
 * AIDEV-NOTE: State manager for Phase 3 active identity collection
 */
public class CollectionState {

    /**
     * Collection status enum
     */
    public enum Status {
        /** Not started */
        NOT_STARTED,
        /** In progress - asking questions */
        IN_PROGRESS,
        /** Completed - basic identity created */
        COMPLETED,
        /** Abandoned by user */
        ABANDONED
    }

    /**
     * Question category for organized collection
     */
    public enum QuestionCategory {
        BASIC_INFO("基本信息"),
        EDUCATION("教育背景"),
        JOB_PREFERENCES("求职偏好"),
        SKILLS("技能特长"),
        EXPERIENCE("实践经验");

        private final String displayName;

        QuestionCategory(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    /**
     * Represents a single question in the collection flow
     */
    public record CollectionQuestion(
            QuestionCategory category,
            String question,
            String field,
            boolean required
    ) {
    }

    // Collection state per user
    private final String jobClawUserId;
    private volatile Status status;
    private final Instant startedAt;
    private Instant lastUpdatedAt;
    private Instant completedAt;

    // Questions tracking
    private final List<CollectionQuestion> askedQuestions = new ArrayList<>();
    private final List<CollectionQuestion> remainingQuestions = new ArrayList<>();
    private CollectionQuestion currentQuestion;

    // Collected answers
    private final Map<String, String> collectedAnswers = new ConcurrentHashMap<>();

    // Channel info for sending messages
    private String activeChannel;
    private String conversationId;

    public CollectionState(String jobClawUserId) {
        this.jobClawUserId = jobClawUserId;
        this.status = Status.NOT_STARTED;
        this.startedAt = Instant.now();
        this.lastUpdatedAt = Instant.now();
    }

    /**
     * Start the collection process
     */
    public void start(String channel, String convId) {
        this.status = Status.IN_PROGRESS;
        this.activeChannel = channel;
        this.conversationId = convId;
        this.lastUpdatedAt = Instant.now();
    }

    /**
     * Mark a question as asked
     */
    public void markQuestionAsked(CollectionQuestion question) {
        this.currentQuestion = question;
        this.askedQuestions.add(question);
        this.remainingQuestions.remove(question);
        this.lastUpdatedAt = Instant.now();
    }

    /**
     * Record an answer
     */
    public void recordAnswer(String field, String answer) {
        if (currentQuestion != null && currentQuestion.field().equals(field)) {
            collectedAnswers.put(field, answer);
            this.lastUpdatedAt = Instant.now();
        }
    }

    /**
     * Mark collection as completed
     */
    public void complete() {
        this.status = Status.COMPLETED;
        this.completedAt = Instant.now();
        this.lastUpdatedAt = Instant.now();
    }

    /**
     * Mark collection as abandoned
     */
    public void abandon() {
        this.status = Status.ABANDONED;
        this.lastUpdatedAt = Instant.now();
    }

    /**
     * Check if collection is in progress
     */
    public boolean isInProgress() {
        return status == Status.IN_PROGRESS;
    }

    /**
     * Check if collection is completed
     */
    public boolean isCompleted() {
        return status == Status.COMPLETED;
    }

    // Getters
    public String getJobClawUserId() { return jobClawUserId; }
    public Status getStatus() { return status; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getLastUpdatedAt() { return lastUpdatedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public List<CollectionQuestion> getAskedQuestions() { return List.copyOf(askedQuestions); }
    public List<CollectionQuestion> getRemainingQuestions() { return List.copyOf(remainingQuestions); }
    public CollectionQuestion getCurrentQuestion() { return currentQuestion; }
    public Map<String, String> getCollectedAnswers() { return Map.copyOf(collectedAnswers); }
    public String getActiveChannel() { return activeChannel; }
    public String getConversationId() { return conversationId; }

    public void setRemainingQuestions(List<CollectionQuestion> questions) {
        this.remainingQuestions.clear();
        this.remainingQuestions.addAll(questions);
    }

    public void setActiveChannel(String activeChannel) {
        this.activeChannel = activeChannel;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    @Override
    public String toString() {
        return "identityCollectionState{" +
                "jobClawUserId='" + jobClawUserId + '\'' +
                ", status=" + status +
                ", askedQuestions=" + askedQuestions.size() +
                ", collectedAnswers=" + collectedAnswers.size() +
                '}';
    }
}
