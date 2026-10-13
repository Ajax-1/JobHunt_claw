package com.git.hui.jobclaw.plugins.plannotebook.model;

import java.util.Locale;

public enum SubTaskState {
    TODO,
    IN_PROGRESS,
    DONE,
    ABANDONED;

    public static SubTaskState from(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Subtask state must not be blank");
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        return switch (normalized) {
            case "PENDING" -> TODO;
            case "COMPLETED" -> DONE;
            case "CANCELLED", "CANCELED" -> ABANDONED;
            default -> valueOf(normalized);
        };
    }
}
