package com.git.hui.jobclaw.core.tools;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.git.hui.jobclaw.core.tasks.RecurringTask;
import com.git.hui.jobclaw.core.tasks.TaskManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;

import java.time.LocalDateTime;
import java.util.List;

import static java.util.Optional.ofNullable;

/**
 * Creates and manages high-level tasks for AI assistants.
 * Each task is persisted as a Markdown file in the workspace directory.
 */
public class TaskTool {

    private static final Logger logger = LoggerFactory.getLogger(TaskTool.class);
    private final TaskManager taskManager;
    private final TaskEventHandler taskEventHandler;


    public TaskTool(TaskManager taskManager, TaskEventHandler taskEventHandler) {
        this.taskManager = taskManager;
        this.taskEventHandler = taskEventHandler;
    }

    public interface TaskEventHandler {

        void taskCreated(String name, String description, String jobClawUserId);

        void taskScheduled(LocalDateTime executionTime, String name, String description, String jobClawUserId);

        void recurringTaskCreated(String cronExpression, String name, String description, String jobClawUserId);

    }

    @Tool(name = "createTask",
            description = """
                    Use this tool to manage high-level tasks that represent major units of work.
                    Tasks are persistent, trackable entities that you can work on backed by JobRunr.
                                
                    ## When to Use:
                    - When a user provides a new goal or assignment.
                    - When a user provides multiple goals (create a separate task for EACH).
                    - To formalize a request into a trackable entity before starting work.
                                
                    ## Constraints:
                    - Name: Short, descriptive identifier (e.g., 'research-market', 'update-docs'). Spaces will be converted to underscores. Must always be in English
                    - Description: Detailed explanation of what needs to be achieved.
                    """)
    public String createTask(
            @JsonPropertyDescription("任务名，始终是英文格式，如 search-email")
            String name,
            @JsonPropertyDescription("任务的描述，使用中文对这个任务进行详细说明")
            String description,
            ToolContext toolContext) {
        logger.info("[TaskTool] createTask called - name: {}, description: {}, jobClawUserId: {}", 
                name, description, toolContext.getContext().get("jobClawUserId"));
        try {
            String jobClawUserId = (String) toolContext.getContext().get("jobClawUserId");
            this.taskManager.create(name, description, jobClawUserId);
            ofNullable(taskEventHandler).ifPresent(x -> x.taskCreated(name, description, jobClawUserId));
            return String.format("Task '%s' has been created successfully.", name);
        } catch (Exception e) {
            logger.error("Failed to create task", e);
            return "Error: Could not create task. " + e.getMessage();
        }
    }

    @Tool(name = "scheduleTask",
            description = """
                    Schedules a task using JobRunr for a specific time in the future.
                    Use this when a user explicitly mentions a time or date (e.g., "Remind me in 10 minutes" or "Schedule in 1 hour").
                                
                    - delaySeconds: The number of seconds from now when the task should run (e.g., 600 for 10 minutes, 3600 for 1 hour).
                    - name: Short, descriptive identifier (e.g., 'monday-morning-sync').
                    - description: Detailed instructions on what the task entails.
                    """)
    public String scheduleTask(
            @JsonPropertyDescription("任务延迟执行的秒数，从当前时间开始计算，如 600 表示10分钟后执行，3600 表示1小时后执行")
            Long delaySeconds,
            @JsonPropertyDescription("任务名，始终是英文格式，如 monday-morning-sync")
            String name,
            @JsonPropertyDescription("任务的描述，使用中文对这个任务进行详细说明")
            String description,
            ToolContext toolContext) {
        logger.info("[TaskTool] scheduleTask called - delaySeconds: {}, name: {}, description: {}, jobClawUserId: {}", 
                delaySeconds, name, description, toolContext.getContext().get("jobClawUserId"));
        try {
            // using string to work around tool call argument parsing exception
            String jobClawUserId = (String) toolContext.getContext().get("jobClawUserId");
            LocalDateTime executionTime = LocalDateTime.now().plusSeconds(delaySeconds);
            this.taskManager.schedule(executionTime, name, description, jobClawUserId);
            ofNullable(taskEventHandler).ifPresent(x -> x.taskScheduled(executionTime,
                    name,
                    description,
                    jobClawUserId));
            return String.format("Task '%s' has been scheduled to execute in %d seconds.", name, delaySeconds);
        } catch (Exception e) {
            logger.error("Failed to schedule task", e);
            return "Error: Could not schedule task. " + e.getMessage();
        }
    }

    @Tool(name = "scheduleRecurringTask",
            description = """
                    Schedules a task using JobRunr that repeats at regular intervals based on a cron expression.
                    Use this for recurring activities like daily reports, weekly checks, etc.
                                
                    - cronExpression: A standard quartz-style cron expression (e.g., '0 12 * * *' for daily at noon or '* * * * *' for every minute. Do not use ? in a cron expression).
                    - name: Short, descriptive identifier (e.g., 'weekly-log-cleanup').
                    - description: Detailed instructions on what the task entails.
                    """)
    public String scheduleRecurringTask(
            @JsonPropertyDescription("Cron表达式，如 0 12 * * * 表示每天中午12点执行，不要使用 ? 符号")
            String cronExpression,
            @JsonPropertyDescription("任务名，始终是英文格式，如 weekly-log-cleanup")
            String name,
            @JsonPropertyDescription("任务的描述，使用中文对这个任务进行详细说明")
            String description,
            ToolContext toolContext) {
        logger.info("[TaskTool] scheduleRecurringTask called - cronExpression: {}, name: {}, description: {}, jobClawUserId: {}", 
                cronExpression, name, description, toolContext.getContext().get("jobClawUserId"));
        try {
            String jobClawUserId = (String) toolContext.getContext().get("jobClawUserId");
            this.taskManager.scheduleRecurrently(cronExpression, name, description, jobClawUserId);
            ofNullable(taskEventHandler).ifPresent(x -> x.recurringTaskCreated(cronExpression,
                    name,
                    description,
                    jobClawUserId));
            return String.format("Task '%s' has been scheduled recurrently with cron expression '%s'.",
                    name,
                    cronExpression);
        } catch (Exception e) {
            logger.error("Failed to schedule recurring task", e);
            return "Error: Could not schedule recurring task. " + e.getMessage();
        }
    }

    @Tool(name = "deleteRecurringTask",
            description = """
                    Deletes a recurring task by name, stopping it from running again.
                    Use this when a user wants to remove, cancel, or stop a recurring task.
                                
                    - name: The name of the recurring task to delete (e.g., 'weekly-log-cleanup').
                    """)
    public String deleteRecurringTask(
            @JsonPropertyDescription("要删除的周期性任务名，必须是英文格式，如 weekly-log-cleanup")
            String name,
            ToolContext toolContext) {
        logger.info("[TaskTool] deleteRecurringTask called - name: {}, jobClawUserId: {}", 
                name, toolContext.getContext().get("jobClawUserId"));
        try {
            String jobClawUserId = (String) toolContext.getContext().get("jobClawUserId");

            this.taskManager.deleteRecurringTask(name, jobClawUserId);
            return String.format("Recurring task '%s' has been deleted successfully.", name);
        } catch (Exception e) {
            logger.error("Failed to delete recurring task", e);
            return "Error: Could not delete recurring task. " + e.getMessage();
        }
    }

    @Tool(name = "listRecurringTasks",
            description = """
                    List all recurring tasks for a specific user.
                    Use this when a user wants to list their recurring tasks.
                    """)
    public String listRecurringTasks(ToolContext toolContext) {
        String jobClawUserId = (String) toolContext.getContext().get("jobClawUserId");
        logger.info("[TaskTool] listRecurringTasks called - jobClawUserId: {}", jobClawUserId);

        List<RecurringTask> allRecurringTasks = taskManager.getRecurringTasksByUser(jobClawUserId);

        StringBuilder sb = new StringBuilder();
        sb.append("Recurring tasks:").append(System.lineSeparator());
        allRecurringTasks.forEach(rt -> {
            sb.append("- id: ").append(rt.getId()).append(System.lineSeparator());
            sb.append("  name: ").append(rt.getName()).append(System.lineSeparator());
            sb.append("  cronExpression: ").append(rt.getCronExpression()).append(System.lineSeparator());
            sb.append("  description: ").append(rt.getDescription(),
                    0,
                    Math.min(rt.getDescription().length(), 100)).append(System.lineSeparator());
            if (rt.getJobClawUserId() != null) {
                sb.append("  userId: ").append(rt.getJobClawUserId()).append(System.lineSeparator());
            }
        });
        return sb.toString();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private TaskManager taskManager;
        private TaskEventHandler taskEventHandler;


        public Builder taskManager(TaskManager taskManager) {
            this.taskManager = taskManager;
            return this;
        }

        public Builder agentTaskEventHandler(TaskEventHandler taskEventHandler) {
            this.taskEventHandler = taskEventHandler;
            return this;
        }

        public TaskTool build() {
            return new TaskTool(this.taskManager, this.taskEventHandler);
        }

    }
}