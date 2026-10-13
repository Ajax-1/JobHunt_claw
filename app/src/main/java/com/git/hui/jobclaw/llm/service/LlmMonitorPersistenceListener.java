package com.git.hui.jobclaw.llm.service;

import com.git.hui.jobclaw.core.monitor.LlmInvocationRecord;
import com.git.hui.jobclaw.core.monitor.LlmRequestRecord;
import com.git.hui.jobclaw.llm.dao.entity.LlmInvocationEntity;
import com.git.hui.jobclaw.llm.dao.entity.LlmRequestEntity;
import com.git.hui.jobclaw.llm.dao.repository.LlmInvocationRepository;
import com.git.hui.jobclaw.llm.dao.repository.LlmRequestRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class LlmMonitorPersistenceListener {
    private final LlmInvocationRepository invocations;
    private final LlmRequestRepository requests;

    public LlmMonitorPersistenceListener(LlmInvocationRepository invocations, LlmRequestRepository requests) {
        this.invocations = invocations;
        this.requests = requests;
    }

    @Async
    @EventListener
    public void save(LlmInvocationRecord r) {
        try {
            LlmInvocationEntity e = new LlmInvocationEntity();
            e.setId(r.invocationId());
            e.setJobClawUserId(r.jobClawUserId());
            e.setConversationId(r.conversationId());
            e.setChannel(r.channel());
            e.setAgent(r.agent());
            e.setOperation(r.operation());
            e.setMode(r.mode());
            e.setOutcome(r.outcome());
            e.setDurationMs(r.durationMs());
            e.setRequestCount(r.requestCount());
            e.setInputTokens(r.inputTokens());
            e.setOutputTokens(r.outputTokens());
            e.setTotalTokens(r.totalTokens());
            e.setEstimatedCost(r.estimatedCost());
            e.setErrorMessage(r.errorMessage());
            e.setCreateTime(r.createTime());
            invocations.save(e);
        } catch (Exception e) {
            log.warn("Failed to persist LLM invocation {}", r.invocationId(), e);
        }
    }

    @Async
    @EventListener
    public void save(LlmRequestRecord r) {
        try {
            LlmRequestEntity e = new LlmRequestEntity();
            e.setId(r.requestId());
            e.setInvocationId(r.invocationId());
            e.setJobClawUserId(r.jobClawUserId());
            e.setConversationId(r.conversationId());
            e.setChannel(r.channel());
            e.setAgent(r.agent());
            e.setOperation(r.operation());
            e.setMode(r.mode());
            e.setProvider(r.provider());
            e.setModel(r.model());
            e.setModelType(r.modelType());
            e.setOutcome(r.outcome());
            e.setDurationMs(r.durationMs());
            e.setInputTokens(r.inputTokens());
            e.setOutputTokens(r.outputTokens());
            e.setTotalTokens(r.totalTokens());
            e.setEstimatedCost(r.estimatedCost());
            e.setErrorMessage(r.errorMessage());
            e.setPromptSample(r.promptSample());
            e.setResponseSample(r.responseSample());
            e.setCreateTime(r.createTime());
            requests.save(e);
        } catch (Exception e) {
            log.warn("Failed to persist LLM request {}", r.requestId(), e);
        }
    }
}
