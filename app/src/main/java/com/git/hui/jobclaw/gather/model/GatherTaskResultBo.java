package com.git.hui.jobclaw.gather.model;

import java.util.List;

/**
 * @date 2025/7/18
 */
public record GatherTaskResultBo(
        String msg,
        List<Long> insertDraftIds,
        List<Long> updateDraftIds,
        List<Long> unchangedDraftIds,
        List<Long> skipDraftIds,
        List<String> failedItems
) {
    public static final String SUCCESS = "success";

    public GatherTaskResultBo {
        insertDraftIds = insertDraftIds == null ? List.of() : insertDraftIds;
        updateDraftIds = updateDraftIds == null ? List.of() : updateDraftIds;
        unchangedDraftIds = unchangedDraftIds == null ? List.of() : unchangedDraftIds;
        skipDraftIds = skipDraftIds == null ? List.of() : skipDraftIds;
        failedItems = failedItems == null ? List.of() : failedItems;
    }

    public GatherTaskResultBo(String msg, List<Long> insertDraftIds, List<Long> updateDraftIds) {
        this(msg, insertDraftIds, updateDraftIds, List.of(), List.of(), List.of());
    }

    public GatherTaskResultBo(String msg, List<Long> insertDraftIds, List<Long> updateDraftIds, List<Long> unchangedDraftIds) {
        this(msg, insertDraftIds, updateDraftIds, unchangedDraftIds, List.of(), List.of());
    }

    public boolean isSuccess() {
        return SUCCESS.equals(msg);
    }
}
