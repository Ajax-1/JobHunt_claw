package com.git.hui.jobclaw.plugins.plannotebook;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.git.hui.jobclaw.core.tools.PlanNotebookCapability;
import com.git.hui.jobclaw.plugins.plannotebook.model.Plan;
import com.git.hui.jobclaw.plugins.plannotebook.model.SubTaskState;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;

import java.util.List;

public class PlanNotebookTool implements PlanNotebookCapability {

    private final PlanNotebook notebook;

    public PlanNotebookTool(PlanNotebook notebook) {
        this.notebook = notebook;
    }

    @Tool(name = "createPlan", description = """
            Create or replace the current structured plan for a complex task.
            Use this for work with multiple ordered steps, then keep every subtask state current.
            Subtask indexes are zero-based.
            """)
    public String createPlan(
            @JsonPropertyDescription("Short name describing the plan goal") String name,
            @JsonPropertyDescription("Ordered, actionable subtask descriptions") List<String> subtasks,
            ToolContext toolContext) {
        return notebook.format(notebook.create(userId(toolContext), name, subtasks));
    }

    @Tool(name = "getCurrentPlan", description = "Show the current user's active plan and progress.")
    public String getCurrentPlan(ToolContext toolContext) {
        return notebook.getCurrent(userId(toolContext))
                .map(notebook::format)
                .orElse("No current plan exists.");
    }

    @Tool(name = "updateSubtaskState", description = """
            Update a zero-based subtask index. Valid states: TODO, IN_PROGRESS, DONE, ABANDONED.
            Keep at most one subtask IN_PROGRESS. Include a concise result when completing or abandoning work.
            """)
    public String updateSubtaskState(
            @JsonPropertyDescription("Zero-based subtask index") int subtaskIndex,
            @JsonPropertyDescription("TODO, IN_PROGRESS, DONE, or ABANDONED") String state,
            @JsonPropertyDescription("Optional result, evidence, or reason") String result,
            ToolContext toolContext) {
        Plan plan = notebook.updateSubtask(userId(toolContext), subtaskIndex, SubTaskState.from(state), result);
        return notebook.format(plan);
    }

    @Tool(name = "finishSubtask", description = "Mark a zero-based subtask index as DONE and record its result.")
    public String finishSubtask(
            @JsonPropertyDescription("Zero-based subtask index") int subtaskIndex,
            @JsonPropertyDescription("Concise completion result or evidence") String result,
            ToolContext toolContext) {
        return notebook.format(notebook.updateSubtask(userId(toolContext), subtaskIndex, SubTaskState.DONE, result));
    }

    @Tool(name = "abandonSubtask", description = "Mark a zero-based subtask index as ABANDONED and record why.")
    public String abandonSubtask(
            @JsonPropertyDescription("Zero-based subtask index") int subtaskIndex,
            @JsonPropertyDescription("Reason the subtask was abandoned") String reason,
            ToolContext toolContext) {
        return notebook.format(notebook.updateSubtask(userId(toolContext), subtaskIndex, SubTaskState.ABANDONED, reason));
    }

    @Tool(name = "clearPlan", description = "Delete the current user's plan after it is no longer needed.")
    public String clearPlan(ToolContext toolContext) {
        notebook.clear(userId(toolContext));
        return "Current plan cleared.";
    }

    private static String userId(ToolContext toolContext) {
        Object value = toolContext.getContext().get("jobClawUserId");
        if (!(value instanceof String userId) || userId.isBlank()) {
            throw new IllegalArgumentException("ToolContext is missing jobClawUserId");
        }
        return userId;
    }
}
