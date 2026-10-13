package com.git.hui.jobclaw.core.tasks;

import java.time.LocalDate;
import java.util.List;

public interface TaskRepository {

    Task save(Task task);

    Task getTaskById(String id);

    /**
     * 获取指定用户的任务列表
     *
     * @param localDate 日期
     * @param status 状态
     * @param jobClawUserId 用户ID
     * @return 任务列表
     */
    List<Task> getTasksByUser(LocalDate localDate, Task.Status status, String jobClawUserId);

    RecurringTask save(RecurringTask recurringTask);

    RecurringTask getRecurringTaskById(String id);

    List<RecurringTask> getAllRecurringTasks();

    /**
     * 获取指定用户的周期性任务列表
     *
     * @param jobClawUserId 用户ID
     * @return 周期性任务列表
     */
    List<RecurringTask> getRecurringTasksByUser(String jobClawUserId);

    void deleteRecurringTask(String id);

}
