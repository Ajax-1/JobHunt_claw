package com.git.hui.jobclaw.llm.service;

import com.git.hui.jobclaw.core.apis.PageListVo;
import com.git.hui.jobclaw.llm.dao.entity.LlmInvocationEntity;
import com.git.hui.jobclaw.llm.dao.entity.LlmRequestEntity;
import com.git.hui.jobclaw.llm.dao.repository.LlmInvocationRepository;
import com.git.hui.jobclaw.llm.dao.repository.LlmRequestRepository;
import com.git.hui.jobclaw.llm.service.vo.LlmCallDetailVo;
import com.git.hui.jobclaw.llm.service.vo.LlmCallVo;
import com.git.hui.jobclaw.llm.service.vo.LlmOverviewVo;
import com.git.hui.jobclaw.llm.service.vo.LlmRequestVo;
import com.git.hui.jobclaw.user.dao.entity.UserEntity;
import com.git.hui.jobclaw.user.dao.repository.UserRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class LlmUsageService {
    private final LlmInvocationRepository repository;
    private final LlmRequestRepository requestRepository;
    private final UserRepository userRepository;

    public LlmUsageService(LlmInvocationRepository repository, LlmRequestRepository requestRepository, UserRepository userRepository) {
        this.repository = repository;
        this.requestRepository = requestRepository;
        this.userRepository = userRepository;
    }

    public LlmOverviewVo overview(String userId) {
        List<LlmInvocationEntity> rows = repository.findAll(spec(userId, null, null, null));
        long success = rows.stream().filter(x -> "SUCCESS".equals(x.getOutcome())).count();
        long duration = rows.stream().map(LlmInvocationEntity::getDurationMs).filter(Objects::nonNull).mapToLong(Long::longValue).sum();
        long tokens = rows.stream().map(LlmInvocationEntity::getTotalTokens).filter(Objects::nonNull).mapToLong(Long::longValue).sum();
        BigDecimal cost = rows.stream().map(LlmInvocationEntity::getEstimatedCost).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        LlmOverviewVo vo = new LlmOverviewVo();
        vo.setCalls(rows.size());
        vo.setSuccessRate(rows.isEmpty() ? 0D : success * 100D / rows.size());
        vo.setAverageDurationMs(rows.isEmpty() ? 0D : duration * 1D / rows.size());
        vo.setTotalTokens(tokens);
        vo.setEstimatedCost(cost);
        return vo;
    }

    public PageListVo<LlmCallVo> calls(String userId, String agent, String operation, String outcome, int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        var result = repository.findAll(spec(userId, agent, operation, outcome),
                PageRequest.of(Math.max(0, page - 1), safeSize).withSort(Sort.by(Sort.Order.desc("createTime"))));
        List<LlmCallVo> list = result.getContent().stream().map(this::toCallVo).toList();
        if (userId == null && !list.isEmpty()) {
            Map<String, String> nickMap = userRepository.findByIdIn(list.stream().map(LlmCallVo::getJobClawUserId).filter(Objects::nonNull).map(Long::valueOf).toList())
                    .stream().collect(Collectors.toMap(u -> String.valueOf(u.getId()), UserEntity::getDisplayName, (a, b) -> a));
            list.forEach(vo -> vo.setNickName(nickMap.get(vo.getJobClawUserId())));
        }
        return PageListVo.of(list, result.getTotalElements(), page, safeSize);
    }

    public LlmCallVo detail(String id, String userId) {
        LlmInvocationEntity entity = repository.findOne(spec(userId, null, null, null).and((root, q, cb) -> cb.equal(root.get("id"), id)))
                .orElseThrow(() -> new NoSuchElementException("LLM call not found"));
        return toCallVo(entity);
    }

    public LlmCallDetailVo userDetail(String id, String userId) {
        LlmCallVo invocation = detail(id, userId);
        List<LlmRequestVo> requests = requestRepository.findByInvocationIdOrderByCreateTimeAsc(id).stream().map(this::toRequestVo).toList();
        LlmCallDetailVo vo = new LlmCallDetailVo();
        vo.setInvocation(invocation);
        vo.setRequests(requests);
        return vo;
    }

    public LlmCallDetailVo adminDetail(String id) {
        LlmCallVo invocation = toCallVo(repository.findById(id).orElseThrow(() -> new NoSuchElementException("LLM call not found")));
        if (invocation.getJobClawUserId() != null) {
            userRepository.findById(Long.valueOf(invocation.getJobClawUserId())).ifPresent(u -> invocation.setNickName(u.getDisplayName()));
        }
        List<LlmRequestVo> requests = requestRepository.findByInvocationIdOrderByCreateTimeAsc(id).stream().map(this::toRequestVo).toList();
        LlmCallDetailVo vo = new LlmCallDetailVo();
        vo.setInvocation(invocation);
        vo.setRequests(requests);
        return vo;
    }

    private LlmCallVo toCallVo(LlmInvocationEntity entity) {
        LlmCallVo vo = new LlmCallVo();
        vo.setId(entity.getId());
        vo.setJobClawUserId(entity.getJobClawUserId());
        vo.setChannel(entity.getChannel());
        vo.setAgent(entity.getAgent());
        vo.setOperation(entity.getOperation());
        vo.setMode(entity.getMode());
        vo.setOutcome(entity.getOutcome());
        vo.setDurationMs(entity.getDurationMs());
        vo.setRequestCount(entity.getRequestCount());
        vo.setInputTokens(entity.getInputTokens());
        vo.setOutputTokens(entity.getOutputTokens());
        vo.setTotalTokens(entity.getTotalTokens());
        vo.setEstimatedCost(entity.getEstimatedCost());
        vo.setCreateTime(entity.getCreateTime());
        return vo;
    }

    private LlmRequestVo toRequestVo(LlmRequestEntity entity) {
        LlmRequestVo vo = new LlmRequestVo();
        vo.setId(entity.getId());
        vo.setInvocationId(entity.getInvocationId());
        vo.setChannel(entity.getChannel());
        vo.setProvider(entity.getProvider());
        vo.setModel(entity.getModel());
        vo.setModelType(entity.getModelType());
        vo.setOutcome(entity.getOutcome());
        vo.setDurationMs(entity.getDurationMs());
        vo.setInputTokens(entity.getInputTokens());
        vo.setOutputTokens(entity.getOutputTokens());
        vo.setTotalTokens(entity.getTotalTokens());
        vo.setEstimatedCost(entity.getEstimatedCost());
        vo.setPromptSample(entity.getPromptSample());
        vo.setResponseSample(entity.getResponseSample());
        vo.setCreateTime(entity.getCreateTime());
        return vo;
    }

    private Specification<LlmInvocationEntity> spec(String userId, String agent, String operation, String outcome) {
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (userId != null) p.add(cb.equal(root.get("jobClawUserId"), userId));
            if (agent != null && !agent.isBlank()) p.add(cb.equal(root.get("agent"), agent));
            if (operation != null && !operation.isBlank()) p.add(cb.equal(root.get("operation"), operation));
            if (outcome != null && !outcome.isBlank()) p.add(cb.equal(root.get("outcome"), outcome));
            return cb.and(p.toArray(Predicate[]::new));
        };
    }
}
