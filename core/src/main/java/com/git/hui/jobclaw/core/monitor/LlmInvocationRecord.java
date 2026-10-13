package com.git.hui.jobclaw.core.monitor;

import java.math.BigDecimal;
import java.time.Instant;

public record LlmInvocationRecord(String invocationId, String jobClawUserId, String conversationId,
                                  String channel, String agent, String operation, String mode,
                                  String outcome, long durationMs, int requestCount, Long inputTokens,
                                  Long outputTokens, Long totalTokens, BigDecimal estimatedCost,
                                  String errorMessage, Instant createTime) {
}
