package com.git.hui.jobclaw.core.configuration;

import lombok.Data;

import java.util.List;

/**
 *
 * @date 2026/4/9
 */
public interface EnvConfigRepository<T extends EnvConfigRepository.EnvConfig> {

    /**
     * 查询所有启用的配置,按优先级排序
     *
     * @param enabled 启用状态
     * @return 配置列表
     */
    List<T> findByEnabledOrderByCreateTimeAsc(Integer enabled);

    void saveOrUpdateConfig(
            String configKey,
            String configValue,
            String configType,
            String configDesc
    );

    @Data
    class EnvConfig {
        private String configKey;
        private String configValue;
        private String configType;
        private String configDesc;
    }


}
