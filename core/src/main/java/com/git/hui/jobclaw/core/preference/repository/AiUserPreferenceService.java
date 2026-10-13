package com.git.hui.jobclaw.core.preference.repository;

import com.git.hui.jobclaw.core.configuration.event.PropertiesRefreshedEvent;
import com.git.hui.jobclaw.core.preference.AiUserPreferenceProperties;
import com.git.hui.jobclaw.core.utils.json.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.Optional;

/**
 * AI用户偏好服务
 * @date 2026/4/23
 */
@Slf4j
@Service
public class AiUserPreferenceService {

    private final AiUserPreferenceRepository aiUserPreferenceRepository;
    private final ApplicationEventPublisher eventPublisher;

    public AiUserPreferenceService(AiUserPreferenceRepository aiUserPreferenceRepository, ApplicationEventPublisher eventPublisher) {
        this.aiUserPreferenceRepository = aiUserPreferenceRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 保存或更新用户偏好配置
     * 如果用户已存在则更新，否则创建新记录
     *
     * @param userId 用户ID
     * @param preference 偏好配置JSON字符串
     * @return 保存后的实体
     */
    @Transactional
    public AiUserPreferenceEntity saveOrUpdate(Long userId, String preference) {
        AiUserPreferenceEntity entity = aiUserPreferenceRepository.findByUserId(userId);
        Date now = new Date();

        if (entity == null) {
            // 创建新记录
            entity = new AiUserPreferenceEntity();
            entity.setUserId(userId);
            entity.setCreateTime(now);
            log.info("创建新的用户偏好配置, userId: {}", userId);
        } else {
            log.info("更新用户偏好配置, userId: {}", userId);
        }

        entity.setPreference(preference);
        entity.setUpdateTime(now);

        var ans = aiUserPreferenceRepository.saveAndFlush(entity);
        eventPublisher.publishEvent(new PropertiesRefreshedEvent(this, AiUserPreferenceProperties.class));
        return ans;
    }

    /**
     * 根据用户ID查询偏好配置
     *
     * @param userId 用户ID
     * @return 用户偏好实体，不存在时返回null
     */
    public AiUserPreferenceEntity findByUserId(Long userId) {
        return aiUserPreferenceRepository.findByUserId(userId);
    }

    public AiUserPreferenceProperties.UserPreferenceEntry loadUserPreferenceConfiguration(Long userId) {
        AiUserPreferenceEntity entity = findByUserId(userId);
        if (entity == null) {
            return null;
        }
        return JsonUtil.toObj(entity.getPreference(), AiUserPreferenceProperties.UserPreferenceEntry.class);
    }

    /**
     * 根据用户ID查询偏好配置（带Optional包装）
     *
     * @param userId 用户ID
     * @return Optional包装的用户偏好实体
     */
    public Optional<AiUserPreferenceEntity> findByUserIdOptional(Long userId) {
        return Optional.ofNullable(aiUserPreferenceRepository.findByUserId(userId));
    }
}
