package com.git.hui.jobclaw.web.model.res;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class GatherSourceVo {
    private Long sourceId;
    private Integer type;
    private String title;
    private String content;
    private String sourceHash;
    private String contentHash;
    private Integer version;
    private String model;
    private String ownerType;
    private String runnerMode;
    private String status;
    private Long lastTaskId;
    private String lastResultSummary;
    private Integer lastInsertCount;
    private Integer lastUpdateCount;
    private Integer lastUnchangedCount;
    private Integer lastFailedCount;
    private Long lastSuccessTime;
    private Integer consecutiveFailCount;
    private Long createTime;
    private Long updateTime;
}
