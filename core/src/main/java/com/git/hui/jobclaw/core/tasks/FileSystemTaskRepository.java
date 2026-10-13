package com.git.hui.jobclaw.core.tasks;

import com.git.hui.jobclaw.core.utils.files.YamlDocument;
import com.git.hui.jobclaw.core.utils.files.YamlParser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static java.util.Optional.ofNullable;

@Component
public class FileSystemTaskRepository implements TaskRepository {

    private final Path taskDir;

    public FileSystemTaskRepository(@Value("${agent.workspace:Unknown}") Resource workspaceDir) throws IOException {
        this.taskDir = workspaceDir.getFile().toPath().resolve("tasks");
    }

    @Override
    public Task save(Task task) {
        Path path = ofNullable(task.getId()).map(Path::of).orElseGet(() -> buildTaskPath(task));
        writeTaskFile(path, task);
        return new Task(path.toAbsolutePath().toString(),
                task.getName(),
                task.getCreatedAt(),
                task.getStatus(),
                task.getDescription(),
                task.getJobClawUserId());
    }

    @Override
    public Task getTaskById(String id) {
        try {
            Path path = Path.of(id);
            Map<String, String> fm = YamlParser.parse(Files.readString(path)).frontmatter();
            String jobClawUserId = fm.get("jobClawUserId");
            if (jobClawUserId == null || jobClawUserId.isEmpty()) {
                throw new IllegalStateException("Task file is missing required field: jobClawUserId");
            }
            return new Task(
                    id,
                    fm.get("task"),
                    Instant.parse(fm.get("createdAt")),
                    Task.Status.valueOf(fm.get("status")),
                    fm.getOrDefault("description", ""),
                    jobClawUserId);
        } catch (IOException e) {
            throw new TaskNotFoundException(id, e);
        }
    }

    @Override
    public List<Task> getTasksByUser(LocalDate date, Task.Status status, String jobClawUserId) {
        Path dir = taskDir.resolve(jobClawUserId).resolve(date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
        if (!Files.exists(dir)) return List.of();
        try (Stream<Path> files = Files.list(dir)) {
            return files
                    .filter(p -> p.getFileName().toString().endsWith(".md"))
                    .map(p -> getTaskById(p.toAbsolutePath().toString()))
                    .filter(t -> (status == null || t.getStatus() == status))
                    .toList();
        } catch (IOException e) {
            throw new RuntimeException("Failed to list tasks for user " + jobClawUserId + " on " + date, e);
        }
    }

    @Override
    public RecurringTask save(RecurringTask recurringTask) {
        String validName = sanitizeName(recurringTask.getName());
        Path dir = ensureDirectory(taskDir.resolve(recurringTask.getJobClawUserId()).resolve("recurring"));
        Path path = dir.resolve(validName + ".md");
        writeRecurringTaskFile(path, recurringTask);
        return new RecurringTask(path.toAbsolutePath().toString(),
                recurringTask.getName(),
                recurringTask.getDescription(),
                recurringTask.getJobClawUserId(),
                recurringTask.getCronExpression());
    }

    @Override
    public RecurringTask getRecurringTaskById(String id) {
        try {
            Path path = Path.of(id);
            Map<String, String> fm = YamlParser.parse(Files.readString(path)).frontmatter();
            String jobClawUserId = fm.get("jobClawUserId");
            if (jobClawUserId == null || jobClawUserId.isEmpty()) {
                throw new IllegalStateException("Recurring task file is missing required field: jobClawUserId");
            }
            String cronExpression = fm.get("cronExpression");
            return new RecurringTask(id,
                    fm.get("task"),
                    fm.getOrDefault("description", ""),
                    jobClawUserId,
                    cronExpression);
        } catch (IOException e) {
            throw new TaskNotFoundException(id, e);
        }
    }

    @Override
    public List<RecurringTask> getAllRecurringTasks() {
        try {
            Path parent = ensureDirectory(taskDir);
            List<RecurringTask> total = new ArrayList<>();
            try (Stream<Path> recurringTasks = Files.list(parent)) {
                recurringTasks.forEach(p -> {
                    Path dir = ensureDirectory(p.resolve("recurring"));
                    try (Stream<Path> subTasks = Files.list(dir)) {
                        var list = subTasks.map(t -> t.toAbsolutePath().toString())
                                .map(this::getRecurringTaskById)
                                .toList();
                        total.addAll(list);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
            }
            return total;
        } catch (IOException e) {
            throw new RuntimeException("Could not list all recurring tasks", e);
        }
    }

    @Override
    public List<RecurringTask> getRecurringTasksByUser(String jobClawUserId) {
        try {
            Path dir = ensureDirectory(taskDir.resolve(jobClawUserId).resolve("recurring"));
            try (Stream<Path> recurringTasks = Files.list(dir)) {
                return recurringTasks
                        .map(p -> p.toAbsolutePath().toString())
                        .map(this::getRecurringTaskById)
                        .toList();
            }
        } catch (IOException e) {
            throw new RuntimeException("Could not list recurring tasks for user " + jobClawUserId, e);
        }
    }


    @Override
    public void deleteRecurringTask(String id) {
        try {
            Files.deleteIfExists(Path.of(id));
        } catch (IOException e) {
            throw new RuntimeException("Failed to delete recurring task file: " + id, e);
        }
    }

    // --- private helpers ---

    private Path buildTaskPath(Task task) {
        LocalDateTime dateTime = task.getCreatedAt().atZone(ZoneId.systemDefault()).toLocalDateTime();
        String dateStr = dateTime.toLocalDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String timeStr = dateTime.toLocalTime().format(DateTimeFormatter.ofPattern("HHmmss"));
        String safeName = sanitizeName(task.getName());
        Path dir = ensureDirectory(taskDir.resolve(task.getJobClawUserId()).resolve(dateStr));
        return dir.resolve(String.format("%s-%s.md", timeStr, safeName));
    }

    private void writeTaskFile(Path path, Task task) {
        if (task.getJobClawUserId() == null || task.getJobClawUserId().isEmpty()) {
            throw new IllegalArgumentException("jobClawUserId is required when saving a task");
        }
        Map<String, String> fm = new LinkedHashMap<>();
        fm.put("task", task.getName());
        fm.put("createdAt", task.getCreatedAt().toString());
        fm.put("status", task.getStatus().toString());
        fm.put("description", task.getDescription());
        fm.put("jobClawUserId", task.getJobClawUserId());
        writeFile(path, YamlParser.serialize(new YamlDocument(fm, null)));
    }

    private void writeRecurringTaskFile(Path path, RecurringTask task) {
        if (task.getJobClawUserId() == null || task.getJobClawUserId().isEmpty()) {
            throw new IllegalArgumentException("jobClawUserId is required when saving a recurring task");
        }
        if (task.getCronExpression() == null || task.getCronExpression().isEmpty()) {
            throw new IllegalArgumentException("cronExpression is required when saving a recurring task");
        }
        Map<String, String> fm = new LinkedHashMap<>();
        fm.put("task", task.getName());
        fm.put("description", task.getDescription());
        fm.put("jobClawUserId", task.getJobClawUserId());
        fm.put("cronExpression", task.getCronExpression());
        writeFile(path, YamlParser.serialize(new YamlDocument(fm, null)));
    }

    private static void writeFile(Path path, String content) {
        try {
            if (!Files.exists(path)) Files.createFile(path);
            Files.writeString(path, content, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write task file: " + path, e);
        }
    }

    private static Path ensureDirectory(Path dir) {
        try {
            Files.createDirectories(dir);
            return dir;
        } catch (IOException e) {
            throw new RuntimeException("Failed to create directory: " + dir, e);
        }
    }

    static String sanitizeName(String name) {
        return name
                .replaceAll("[^a-zA-Z0-9._-]", "_")
                .replaceAll("_{2,}", "_");
    }
}
