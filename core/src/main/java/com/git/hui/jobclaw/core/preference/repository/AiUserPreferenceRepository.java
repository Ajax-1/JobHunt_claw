package com.git.hui.jobclaw.core.preference.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * AI用户偏好数据访问接口
 * @date 2026/4/23
 */
public interface AiUserPreferenceRepository extends JpaRepository<AiUserPreferenceEntity, Long>, JpaSpecificationExecutor<AiUserPreferenceEntity> {

    /**
     * 根据用户ID查询偏好配置
     * @param userId 用户ID
     * @return 用户偏好实体
     */
    AiUserPreferenceEntity findByUserId(Long userId);
}
