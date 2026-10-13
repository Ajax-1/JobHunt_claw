package com.git.hui.jobclaw.core.tasks;

import org.jobrunr.jobs.Job;
import org.jobrunr.jobs.states.StateName;
import org.jobrunr.scheduling.JobScheduler;
import org.jobrunr.storage.Paging;
import org.jobrunr.storage.StorageProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Component
public class TaskManager {

    private static final Logger log = LoggerFactory.getLogger(TaskManager.class);
    private final JobScheduler jobScheduler;
    private final StorageProvider storageProvider;
    private final TaskRepository taskRepository;

    public TaskManager(JobScheduler jobScheduler, StorageProvider storageProvider, TaskRepository taskRepository) {
        this.jobScheduler = jobScheduler;
        this.storageProvider = storageProvider;
        this.taskRepository = taskRepository;
    }

    /**
     * 为指定用户创建任务
     *
     * @param name 任务名称
     * @param description 任务描述
     * @param jobClawUserId 用户ID（必填）
     */
    public void create(String name, String description, String jobClawUserId) {
        Task task = taskRepository.save(Task.newTask(name, description, jobClawUserId));
        jobScheduler.<TaskHandler>enqueue(x -> x.executeTask(task.getId()));
        log.info("Task '{}' ({}) has been created for user {}.", task.getName(), task.getId(), jobClawUserId);
    }

    /**
     * 为指定用户调度任务
     *
     * @param executionTime 执行时间（LocalDateTime，使用系统默认时区）
     * @param name 任务名称
     * @param description 任务描述
     * @param jobClawUserId 用户ID（必填）
     */
    public void schedule(LocalDateTime executionTime, String name, String description, String jobClawUserId) {
        // 将 LocalDateTime 转换为 Instant（UTC时间戳），使用系统默认时区
        // 注意：LocalDateTime 本身无时区信息，需要指定时区才能转换为 Instant
        Instant createdAt = executionTime.atZone(ZoneId.systemDefault()).toInstant();

        Task task = taskRepository.save(Task.newTask(name, createdAt, description, jobClawUserId));

        // JobRunr 的 schedule 方法接受 LocalDateTime，会在该时间点执行
        jobScheduler.<TaskHandler>schedule(executionTime, x -> x.executeTask(task.getId()));

        log.info("Task '{}' ({}) has been scheduled at {} (UTC: {}) for user {}.",
                task.getName(), task.getId(), executionTime, createdAt, jobClawUserId);
    }

    /**
     * 为指定用户调度周期性任务
     *
     * @param cronExpression cron表达式
     * @param name 任务名称
     * @param description 任务描述
     * @param jobClawUserId 用户ID（必填）
     */
    public void scheduleRecurrently(String cronExpression, String name, String description, String jobClawUserId) {
        RecurringTask recurringTask = taskRepository.save(RecurringTask.newRecurringTask(name,
                description,
                jobClawUserId,
                cronExpression));
        jobScheduler.<RecurringTaskHandler>scheduleRecurrently(recurringTask.getName(),
                cronExpression,
                x -> x.executeTask(recurringTask.getId()));
        log.info("Task '{}' ({}) has been scheduled recurrently with cronExpression {} for user {}.",
                name,
                recurringTask.getId(),
                cronExpression,
                jobClawUserId);
    }

    /**
     * 删除指定用户的周期性任务
     *
     * @param name 任务名称
     * @param jobClawUserId 用户ID（必填）
     */
    public void deleteRecurringTask(String name, String jobClawUserId) {
        RecurringTask recurringTask = taskRepository.getRecurringTasksByUser(jobClawUserId)
                .stream()
                .filter(x -> x.getName().equals(name))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Recurring task with name " + name + " was not found for user " + jobClawUserId));
        jobScheduler.deleteRecurringJob(recurringTask.getName());
        List<Job> jobList = storageProvider.getJobList(StateName.SCHEDULED,
                Paging.AmountBasedList.ascOnUpdatedAt(1000));
        jobList.stream()
                .filter(j -> j.getRecurringJobId().map(recurringTask.getName()::equals).orElse(false))
                .map(Job::getId)
                .findFirst()
                .ifPresent(jobScheduler::delete);
        taskRepository.deleteRecurringTask(recurringTask.getId());
        log.info("Recurring task '{}' ({}) has been deleted for user {}.", name, recurringTask.getId(), jobClawUserId);
    }

    public List<RecurringTask> getAllRecurringTasks() {
        return taskRepository.getAllRecurringTasks();
    }

    /**
     * 获取指定用户的周期性任务列表
     *
     * @param jobClawUserId 用户ID
     * @return 周期性任务列表
     */
    public List<RecurringTask> getRecurringTasksByUser(String jobClawUserId) {
        return taskRepository.getRecurringTasksByUser(jobClawUserId);
    }

    public void createTaskFromRecurringTask(RecurringTask recurringTask) {
        Task task = taskRepository.save(Task.newTask(recurringTask.getName(),
                recurringTask.getDescription(),
                recurringTask.getJobClawUserId()));
        jobScheduler.<TaskHandler>enqueue(x -> x.executeTask(task.getId()));
        log.info("Task '{}' ({}) has been created from recurring task for user {}.",
                task.getName(),
                task.getId(),
                recurringTask.getJobClawUserId());
    }
}
