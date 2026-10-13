package com.git.hui.jobclaw.configs.service;

import com.git.hui.jobclaw.configs.dao.entity.GlobalEnvConfigEntity;
import com.git.hui.jobclaw.configs.dao.repository.GlobalEnvConfigRepository;
import com.git.hui.jobclaw.core.configuration.EnvConfigRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.util.List;

/**
 * 全局环境配置服务
 *
 * @date 2026/4/9
 */
@Slf4j
@Service
public class GlobalEnvConfigService implements EnvConfigRepository<GlobalEnvConfigEntity> {

    @Autowired
    private GlobalEnvConfigRepository configRepository;

    @Autowired
    private DataSource dataSource;

    @Override
    public List<GlobalEnvConfigEntity> findByEnabledOrderByCreateTimeAsc(Integer enabled) {
        return configRepository.findByEnabledOrderByCreateTimeAsc(enabled);
    }

    public void deleteByConfigKeyPrefix(String configKeyPrefix) {
        configRepository.deleteByConfigKeyStartingWith(configKeyPrefix);
    }

    public List<GlobalEnvConfigEntity> findByConfigKeyPrefix(String configKeyPrefix) {
        return configRepository.findByConfigKeyStartingWith(configKeyPrefix);
    }

    /**
     * 保存或更新配置(自动根据数据库类型选择合适的方法)
     *
     * @param configKey   配置键
     * @param configValue 配置值
     * @param configType  配置类型
     * @param configDesc  配置描述
     */
    @Override
    public void saveOrUpdateConfig(String configKey, String configValue, String configType, String configDesc) {
        try {
            String dbName = getDatabaseProductName();

            if (dbName.toLowerCase().contains("mysql")) {
                log.debug("Using MySQL upsert method for config: {}", configKey);
                configRepository.saveOrUpdateConfigMySQL(configKey, configValue, configType, configDesc);
            } else if (dbName.toLowerCase().contains("h2")) {
                log.debug("Using H2 upsert method for config: {}", configKey);
                configRepository.saveOrUpdateConfigH2(configKey, configValue, configType, configDesc);
            } else {
                log.warn("Unknown database type: {}, trying H2 method", dbName);
                configRepository.saveOrUpdateConfigH2(configKey, configValue, configType, configDesc);
            }
        } catch (Exception e) {
            log.error("Failed to save or update config: {}", configKey, e);
            throw new RuntimeException("保存配置失败: " + e.getMessage(), e);
        }
    }

    /**
     * 获取数据库产品名称
     */
    private String getDatabaseProductName() {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            return metaData.getDatabaseProductName();
        } catch (Exception e) {
            log.error("Failed to get database product name", e);
            return "unknown";
        }
    }
}
