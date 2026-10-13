package com.git.hui.jobclaw.plugins.plannotebook.model;

import java.time.Instant;
import java.util.List;

public record Plan(
        String id,
        String name,
        List<SubTask> subtasks,
        Instant createdAt,
        Instant updatedAt
) {
    public Plan {
        subtasks = List.copyOf(subtasks);
    }

    public Plan withSubtasks(List<SubTask> updatedSubtasks, Instant now) {
        return new Plan(id, name, updatedSubtasks, createdAt, now);
    }
}
