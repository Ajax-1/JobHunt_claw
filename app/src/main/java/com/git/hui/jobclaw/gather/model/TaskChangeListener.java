package com.git.hui.jobclaw.gather.model;

import com.git.hui.jobclaw.constants.gather.GatherTaskStateEnum;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 任务变更监听器
 *
 * @date 2025/7/18
 */
@Getter
public class TaskChangeListener extends ApplicationEvent {

    private final Long taskId;
    private GatherTaskStateEnum state;

    public TaskChangeListener(Object source, Long taskId, GatherTaskStateEnum state) {
        super(source);
        this.taskId = taskId;
        this.state = state;
    }
}
