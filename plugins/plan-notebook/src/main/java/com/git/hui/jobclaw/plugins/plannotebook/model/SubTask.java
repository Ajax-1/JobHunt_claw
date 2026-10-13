package com.git.hui.jobclaw.plugins.plannotebook.model;

import java.time.Instant;

public record SubTask(
        int index,
        String description,
        SubTaskState state,
        String result,
        Instant updatedAt
) {
    public SubTask withState(SubTaskState newState, String newResult, Instant now) {
        return new SubTask(index, description, newState, newResult, now);
    }
}
