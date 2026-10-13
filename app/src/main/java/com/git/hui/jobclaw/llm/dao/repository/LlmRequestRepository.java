package com.git.hui.jobclaw.llm.dao.repository;

import com.git.hui.jobclaw.llm.dao.entity.LlmRequestEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface LlmRequestRepository extends JpaRepository<LlmRequestEntity, String>, JpaSpecificationExecutor<LlmRequestEntity> {
    List<LlmRequestEntity> findByInvocationIdOrderByCreateTimeAsc(String invocationId);
}
