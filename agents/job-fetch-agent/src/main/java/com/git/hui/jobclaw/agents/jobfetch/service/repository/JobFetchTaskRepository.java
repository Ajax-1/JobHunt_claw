package com.git.hui.jobclaw.agents.jobfetch.service.repository;

import com.git.hui.jobclaw.agents.jobfetch.service.model.JobFetchTaskEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 职位抓取任务Repository
 *
 * @date 2026/4/20
 */
@Repository
public interface JobFetchTaskRepository extends JpaRepository<JobFetchTaskEntity, Long> {
    
    /**
     * 根据任务ID查询
     */
    Optional<JobFetchTaskEntity> findByTaskId(String taskId);
    
    /**
     * 根据用户ID和任务ID查询
     */
    Optional<JobFetchTaskEntity> findByJobClawUserIdAndTaskId(String jobClawUserId, String taskId);
    
    /**
     * 查询用户的所有任务(按创建时间倒序)
     */
    List<JobFetchTaskEntity> findByJobClawUserIdOrderByCreateTimeDesc(String jobClawUserId);
}
