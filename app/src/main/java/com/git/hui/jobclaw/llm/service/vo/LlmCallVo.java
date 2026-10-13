package com.git.hui.jobclaw.llm.service.vo;

import lombok.Data;
import java.math.BigDecimal;
import java.time.Instant;

@Data
public class LlmCallVo {
    private String id;
    private String jobClawUserId;
    private String nickName;
    private String channel;
    private String agent;
    private String operation;
    private String mode;
    private String outcome;
    private Long durationMs;
    private Integer requestCount;
    private Long inputTokens;
    private Long outputTokens;
    private Long totalTokens;
    private BigDecimal estimatedCost;
    private Instant createTime;
}
