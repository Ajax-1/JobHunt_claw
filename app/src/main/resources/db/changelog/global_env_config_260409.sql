-- 全局环境配置表
CREATE TABLE `global_env_config`
(
    `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `config_key`   VARCHAR(256) NOT NULL DEFAULT '' COMMENT '配置键(支持嵌套key,如: spring.datasource.url)',
    `config_value` VARCHAR(2048) NOT NULL DEFAULT '' COMMENT '配置值',
    `config_desc`  VARCHAR(512) NOT NULL DEFAULT '' COMMENT '配置说明',
    `config_type`  VARCHAR(32) NOT NULL DEFAULT 'string' COMMENT '配置类型: string, int, boolean, json等',
    `enabled`      TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用: 0-禁用, 1-启用',
    `priority`     INT NOT NULL DEFAULT 0 COMMENT '优先级(数值越小优先级越高)',
    `create_time`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_config_key` (`config_key`),
    KEY `ix_enabled_priority` (`enabled`, `priority`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='全局环境配置表';

-- 插入示例配置数据
INSERT INTO `global_env_config` (`config_key`, `config_value`, `config_desc`, `config_type`, `enabled`, `priority`) VALUES
('jobclaw.site.websiteName', '校招派', '网站名称', 'string', 1, 100),
('jobclaw.feature.enableNotification', 'true', '是否启用通知功能', 'boolean', 1, 100),
('jobclaw.gather.maxRetryCount', '3', '数据采集最大重试次数', 'int', 1, 100);
