package com.git.hui.jobclaw.llm.dao.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.Instant;

@Data
@Entity(name = "llm_invocation")
public class LlmInvocationEntity {
    @Id private String id;
    private String jobClawUserId;
    private String conversationId;
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
    @Column(length = 2000) private String errorMessage;
    private Instant createTime;
}
