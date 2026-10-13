package com.git.hui.jobclaw.llm.service.vo;

import lombok.Data;
import java.math.BigDecimal;
import java.time.Instant;

@Data
public class LlmRequestVo {
    private String id;
    private String invocationId;
    private String channel;
    private String provider;
    private String model;
    private String modelType;
    private String outcome;
    private Long durationMs;
    private Long inputTokens;
    private Long outputTokens;
    private Long totalTokens;
    private BigDecimal estimatedCost;
    private String promptSample;
    private String responseSample;
    private Instant createTime;
}
