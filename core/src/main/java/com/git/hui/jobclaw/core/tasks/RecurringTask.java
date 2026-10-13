package com.git.hui.jobclaw.core.tasks;

import lombok.Getter;

@Getter
public class RecurringTask {

    private final String id;
    private final String name;
    private final String description;
    private final String jobClawUserId;
    private final String cronExpression;

    public RecurringTask(String id, String name, String description, String jobClawUserId, String cronExpression) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.jobClawUserId = jobClawUserId;
        this.cronExpression = cronExpression;
    }

    public static RecurringTask newRecurringTask(String name, String description, String jobClawUserId, String cronExpression) {
        if (jobClawUserId == null || jobClawUserId.isEmpty()) {
            throw new IllegalArgumentException("jobClawUserId is required");
        }
        if (cronExpression == null || cronExpression.isEmpty()) {
            throw new IllegalArgumentException("cronExpression is required");
        }
        return new RecurringTask(null, name, description, jobClawUserId, cronExpression);
    }

    @Override
    public String toString() {
        return "Recurring Task '" + name + "'";
    }
}
