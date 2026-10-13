package com.git.hui.jobclaw.core.tasks;

import lombok.Getter;

import java.time.Instant;

@Getter
public class Task {

    public enum Status {
        todo, in_progress, completed, awaiting_human_input
    }

    private final String id;
    private final String name;
    private final Instant createdAt;
    private final Status status;
    private final String description;
    private final String jobClawUserId;

    public Task(String id, String name, Instant createdAt, Status status, String description, String jobClawUserId) {
        this.id = id;
        this.name = name;
        this.createdAt = createdAt;
        this.status = status;
        this.description = description;
        this.jobClawUserId = jobClawUserId;
    }

    public static Task newTask(String name, String description, String jobClawUserId) {
        if (jobClawUserId == null || jobClawUserId.isEmpty()) {
            throw new IllegalArgumentException("jobClawUserId is required");
        }
        return new Task(null, name, Instant.now(), Status.todo, description, jobClawUserId);
    }

    public static Task newTask(String name, Instant createdAt, String description, String jobClawUserId) {
        if (jobClawUserId == null || jobClawUserId.isEmpty()) {
            throw new IllegalArgumentException("jobClawUserId is required");
        }
        return new Task(null, name, createdAt, Status.todo, description, jobClawUserId);
    }

    public Task withStatus(Status newStatus) {
        return new Task(id, name, createdAt, newStatus, description, jobClawUserId);
    }

    public Task withFeedback(String feedback) {
        return new Task(id, name, createdAt, status, description + "\n\nAgent feedback: " + feedback, jobClawUserId);
    }

    @Override
    public String toString() {
        return "Task '" + name + "'";
    }
}
