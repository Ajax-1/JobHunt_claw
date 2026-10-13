package com.git.hui.jobclaw.plugins.plannotebook;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.git.hui.jobclaw.core.utils.json.JsonUtil;
import com.git.hui.jobclaw.plugins.plannotebook.model.Plan;
import com.git.hui.jobclaw.plugins.plannotebook.model.SubTask;
import com.git.hui.jobclaw.plugins.plannotebook.model.SubTaskState;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stores one current plan per user under the agent workspace.
 * AIDEV-NOTE: Per-user persisted plans.
 */
public class PlanNotebook {

    private static final String FILE_NAME = "plan-notebook.json";

    private final Path usersDir;
    private final ConcurrentHashMap<String, Object> userLocks = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    public PlanNotebook(Resource workspace) throws IOException {
        this.usersDir = workspace.getFile().toPath().resolve("users").toAbsolutePath().normalize();
        this.objectMapper = JsonUtil.getMapper().copy().findAndRegisterModules();
    }

    public Plan create(String userId, String name, List<String> descriptions) {
        requireText(name, "Plan name");
        if (descriptions == null || descriptions.isEmpty()) {
            throw new IllegalArgumentException("A plan must contain at least one subtask");
        }

        Instant now = Instant.now();
        List<SubTask> subtasks = new ArrayList<>();
        for (int i = 0; i < descriptions.size(); i++) {
            requireText(descriptions.get(i), "Subtask description at index " + i);
            subtasks.add(new SubTask(i, descriptions.get(i).trim(), SubTaskState.TODO, null, now));
        }
        Plan plan = new Plan(UUID.randomUUID().toString(), name.trim(), subtasks, now, now);
        return save(userId, plan);
    }

    public Optional<Plan> getCurrent(String userId) {
        synchronized (lock(userId)) {
            Path file = resolveFile(userId);
            if (!Files.exists(file)) {
                return Optional.empty();
            }
            try {
                return Optional.of(objectMapper.readValue(file.toFile(), Plan.class));
            } catch (IOException e) {
                throw new IllegalStateException("Failed to read plan for user " + userId, e);
            }
        }
    }

    public Plan updateSubtask(String userId, int index, SubTaskState state, String result) {
        synchronized (lock(userId)) {
            Plan current = getCurrent(userId)
                    .orElseThrow(() -> new IllegalStateException("No current plan exists"));
            if (index < 0 || index >= current.subtasks().size()) {
                throw new IllegalArgumentException("Subtask index out of range: " + index);
            }
            if (state == SubTaskState.IN_PROGRESS) {
                boolean anotherInProgress = current.subtasks().stream()
                        .anyMatch(task -> task.index() != index && task.state() == SubTaskState.IN_PROGRESS);
                if (anotherInProgress) {
                    throw new IllegalStateException("Only one subtask can be IN_PROGRESS at a time");
                }
            }

            List<SubTask> updated = new ArrayList<>(current.subtasks());
            updated.set(index, updated.get(index).withState(state, blankToNull(result), Instant.now()));
            return save(userId, current.withSubtasks(updated, Instant.now()));
        }
    }

    public void clear(String userId) {
        synchronized (lock(userId)) {
            try {
                Files.deleteIfExists(resolveFile(userId));
            } catch (IOException e) {
                throw new IllegalStateException("Failed to clear plan for user " + userId, e);
            }
        }
    }

    public String format(Plan plan) {
        long done = plan.subtasks().stream().filter(task -> task.state() == SubTaskState.DONE).count();
        StringBuilder output = new StringBuilder()
                .append("Plan: ").append(plan.name()).append(System.lineSeparator())
                .append("Progress: ").append(done).append("/").append(plan.subtasks().size())
                .append(System.lineSeparator());
        for (SubTask task : plan.subtasks()) {
            output.append("[").append(task.index()).append("] ")
                    .append(task.state()).append(" - ").append(task.description());
            if (task.result() != null) {
                output.append(" (result: ").append(task.result()).append(")");
            }
            output.append(System.lineSeparator());
        }
        return output.toString().trim();
    }

    public String generateHint(String userId) {
        return getCurrent(userId).map(plan -> {
            Optional<SubTask> next = plan.subtasks().stream()
                    .filter(task -> task.state() == SubTaskState.IN_PROGRESS)
                    .findFirst()
                    .or(() -> plan.subtasks().stream().filter(task -> task.state() == SubTaskState.TODO).findFirst());
            return "<plan-notebook-hint>\n" + format(plan)
                    + "\nNext action: " + next.map(SubTask::description).orElse("Plan complete")
                    + "\nKeep the plan updated as work progresses.\n</plan-notebook-hint>";
        }).orElse("");
    }

    private Plan save(String userId, Plan plan) {
        synchronized (lock(userId)) {
            Path file = resolveFile(userId);
            Path temp = file.resolveSibling(FILE_NAME + ".tmp");
            try {
                Files.createDirectories(file.getParent());
                objectMapper.writerWithDefaultPrettyPrinter().writeValue(temp.toFile(), plan);
                try {
                    Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (IOException atomicMoveFailure) {
                    Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
                }
                return plan;
            } catch (IOException e) {
                throw new IllegalStateException("Failed to save plan for user " + userId, e);
            }
        }
    }

    private Path resolveFile(String userId) {
        requireText(userId, "jobClawUserId");
        // 对 userId 进行严格的验证，确保其不包含非法路径字符，防止攻击者利用该功能执行任意文件操作或访问敏感文件
        // 验证 userId 只包含允许的字符 (字母、数字、下划线、连字符)
        if (!userId.matches("^[a-zA-Z0-9_-]+$")) {
            throw new IllegalArgumentException("Invalid jobClawUserId: contains invalid characters");
        }
        Path file = usersDir.resolve(userId).resolve(FILE_NAME).toAbsolutePath().normalize();
        if (!file.startsWith(usersDir)) {
            throw new IllegalArgumentException("Invalid jobClawUserId");
        }
        return file;
    }

    private Object lock(String userId) {
        requireText(userId, "jobClawUserId");
        return userLocks.computeIfAbsent(userId, ignored -> new Object());
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
