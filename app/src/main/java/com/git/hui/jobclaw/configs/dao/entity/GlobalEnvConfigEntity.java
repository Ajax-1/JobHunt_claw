package com.git.hui.jobclaw.configs.dao.entity;

import com.git.hui.jobclaw.core.configuration.EnvConfigRepository;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.Accessors;
import org.hibernate.annotations.DynamicUpdate;

import java.util.Date;

/**
 * 全局环境配置表
 * 用于存储需要从数据库加载到Spring Environment的配置项
 *
 * @date 2026/4/9
 */
@Data
@DynamicUpdate
@Entity(name = "global_env_config")
@Table(name = "global_env_config", uniqueConstraints = {
    @UniqueConstraint(name = "uk_config_key", columnNames = "config_key")
})
public class GlobalEnvConfigEntity extends EnvConfigRepository.EnvConfig {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 配置键(支持嵌套key,如: spring.datasource.url)
     */
    @Column(name = "config_key", nullable = false, length = 256)
    private String configKey;

    /**
     * 配置值
     */
    @Column(name = "config_value", nullable = false, length = 2048)
    private String configValue;

    /**
     * 配置说明
     */
    @Column(name = "config_desc", length = 512)
    private String configDesc;

    /**
     * 配置类型: string, int, boolean, json等
     */
    @Column(name = "config_type", length = 32)
    private String configType;

    /**
     * 是否启用: 0-禁用, 1-启用
     */
    @Column(name = "enabled", nullable = false)
    private Integer enabled;
    /**
     * 创建时间
     */
    @Column(name = "create_time")
    private Date createTime;

    /**
     * 更新时间
     */
    @Column(name = "update_time")
    private Date updateTime;
}
