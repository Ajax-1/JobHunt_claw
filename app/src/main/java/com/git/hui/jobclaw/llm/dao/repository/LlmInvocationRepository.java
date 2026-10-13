package com.git.hui.jobclaw.llm.dao.repository;

import com.git.hui.jobclaw.llm.dao.entity.LlmInvocationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface LlmInvocationRepository extends JpaRepository<LlmInvocationEntity, String>, JpaSpecificationExecutor<LlmInvocationEntity> {
}
