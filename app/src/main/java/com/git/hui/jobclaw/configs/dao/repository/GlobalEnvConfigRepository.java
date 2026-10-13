package com.git.hui.jobclaw.configs.dao.repository;

import com.git.hui.jobclaw.configs.dao.entity.GlobalEnvConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 全局环境配置Repository
 *
 * @date 2026/4/9
 */
public interface GlobalEnvConfigRepository extends JpaRepository<GlobalEnvConfigEntity, Long>, JpaSpecificationExecutor<GlobalEnvConfigEntity> {

    /**
     * 查询所有启用的配置,按优先级排序
     *
     * @param enabled 启用状态
     * @return 配置列表
     */
    List<GlobalEnvConfigEntity> findByEnabledOrderByCreateTimeAsc(Integer enabled);

    /**
     * 根据配置键查询
     *
     * @param configKey 配置键
     * @return 配置实体
     */
    GlobalEnvConfigEntity findByConfigKey(String configKey);

    /**
     * 根据配置键前缀查询。
     *
     * @param configKeyPrefix 配置键前缀
     * @return 配置列表
     */
    List<GlobalEnvConfigEntity> findByConfigKeyStartingWith(String configKeyPrefix);

    /**
     * 删除指定配置前缀下的配置项。
     *
     * @param configKeyPrefix 配置键前缀
     */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "DELETE FROM global_env_config WHERE config_key LIKE CONCAT(:configKeyPrefix, '%')", nativeQuery = true)
    void deleteByConfigKeyStartingWith(@Param("configKeyPrefix") String configKeyPrefix);


    /**
     * 保存或更新配置 - H2数据库版本(存在则更新,不存在则插入)
     *
     * @param configKey   配置键
     * @param configValue 配置值
     * @param configType  配置类型
     * @param configDesc  配置描述
     */
    @Transactional
    @Modifying
    @Query(value = "MERGE INTO global_env_config (config_key, config_value, config_type, config_desc, enabled, create_time, update_time) " +
            "KEY (config_key) " +
            "VALUES (:configKey, :configValue, :configType, :configDesc, 1, NOW(), NOW())",
            nativeQuery = true)
    void saveOrUpdateConfigH2(
            @Param("configKey") String configKey,
            @Param("configValue") String configValue,
            @Param("configType") String configType,
            @Param("configDesc") String configDesc
    );

    /**
     * 保存或更新配置 - MySQL版本(存在则更新,不存在则插入)
     *
     * @param configKey   配置键
     * @param configValue 配置值
     * @param configType  配置类型
     * @param configDesc  配置描述
     */
    @Transactional
    @Modifying
    @Query(value = "INSERT INTO global_env_config (config_key, config_value, config_type, config_desc, enabled, create_time, update_time) " +
            "VALUES (:configKey, :configValue, :configType, :configDesc, 1, NOW(), NOW()) " +
            "ON DUPLICATE KEY UPDATE " +
            "config_value = :configValue, " +
            "config_type = :configType, " +
            "config_desc = :configDesc, " +
            "update_time = NOW()",
            nativeQuery = true)
    void saveOrUpdateConfigMySQL(
            @Param("configKey") String configKey,
            @Param("configValue") String configValue,
            @Param("configType") String configType,
            @Param("configDesc") String configDesc
    );
}
