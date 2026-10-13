package com.git.hui.jobclaw.llm.dao.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.Instant;

@Data
@Entity(name = "llm_request")
public class LlmRequestEntity {
    @Id private String id;
    private String invocationId;
    private String jobClawUserId;
    private String conversationId;
    private String channel;
    private String agent;
    private String operation;
    private String mode;
    private String provider;
    private String model;
    private String modelType;
    private String outcome;
    private Long durationMs;
    private Long inputTokens;
    private Long outputTokens;
    private Long totalTokens;
    private BigDecimal estimatedCost;
    @Column(length = 2000) private String errorMessage;
    @Lob private String promptSample;
    @Lob private String responseSample;
    private Instant createTime;
}
