package com.git.hui.jobclaw.core.monitor;

import java.math.BigDecimal;
import java.time.Instant;

public record LlmRequestRecord(String requestId, String invocationId, String jobClawUserId,
                               String conversationId, String channel, String agent, String operation,
                               String mode, String provider, String model, String modelType,
                               String outcome, long durationMs, Long inputTokens, Long outputTokens,
                               Long totalTokens, BigDecimal estimatedCost, String errorMessage,
                               String promptSample, String responseSample, Instant createTime) {
}
