package com.git.hui.jobclaw.application.dao.repository;

import com.git.hui.jobclaw.application.dao.entity.JobApplicationEntity;
import com.git.hui.jobclaw.application.model.ApplicationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface JobApplicationRepository extends JpaRepository<JobApplicationEntity, Long> {
    Optional<JobApplicationEntity> findByUserIdAndJobId(Long userId, Long jobId);

    Optional<JobApplicationEntity> findByIdAndUserId(Long id, Long userId);

    Page<JobApplicationEntity> findByUserId(Long userId, Pageable pageable);

    Page<JobApplicationEntity> findByUserIdAndStatus(Long userId, ApplicationStatus status, Pageable pageable);
}
