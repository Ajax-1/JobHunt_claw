package com.git.hui.jobclaw.gather.model;

import com.git.hui.jobclaw.constants.gather.GatherTargetTypeEnum;

/**
 * @date 2025/7/18
 */
public record GatherTaskProcessBo(Long taskId, GatherTargetTypeEnum type, String model, String content,
                                  Long sourceId, Integer sourceVersion, String runnerType) {
}
