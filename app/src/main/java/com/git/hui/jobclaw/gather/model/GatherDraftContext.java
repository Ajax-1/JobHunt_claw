package com.git.hui.jobclaw.gather.model;

import com.git.hui.jobclaw.constants.gather.GatherRunnerTypes;

/**
 * 草稿保存时关联采集来源和执行任务。
 */
public record GatherDraftContext(Long sourceId, Long taskId, String runnerType) {
    public static GatherDraftContext empty() {
        return new GatherDraftContext(null, null, GatherRunnerTypes.ADMIN);
    }

    public String normalizedRunnerType() {
        return GatherRunnerTypes.normalize(runnerType);
    }
}
