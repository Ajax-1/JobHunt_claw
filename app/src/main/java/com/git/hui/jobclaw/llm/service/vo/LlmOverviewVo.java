package com.git.hui.jobclaw.llm.service.vo;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class LlmOverviewVo {
    private long calls;
    private double successRate;
    private double averageDurationMs;
    private long totalTokens;
    private BigDecimal estimatedCost;
}
