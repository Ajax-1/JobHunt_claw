CREATE TABLE IF NOT EXISTS `gather_source`
(
    `id`                     BIGINT       NOT NULL AUTO_INCREMENT COMMENT '采集源ID',
    `type`                   INT          NOT NULL DEFAULT 0 COMMENT '来源类型，0表示待自动分类',
    `title`                  VARCHAR(256) NOT NULL DEFAULT '' COMMENT '来源标题或摘要',
    `content`                LONGTEXT     NULL COMMENT '来源内容或文件路径',
    `source_hash`            VARCHAR(128) NOT NULL DEFAULT '' COMMENT '规范化来源指纹',
    `content_hash`           VARCHAR(128) NOT NULL DEFAULT '' COMMENT '当前内容指纹',
    `version`                INT          NOT NULL DEFAULT 1 COMMENT '来源版本',
    `model`                  VARCHAR(128) NOT NULL DEFAULT '' COMMENT '默认模型',
    `owner_type`             VARCHAR(32)  NOT NULL DEFAULT 'admin' COMMENT '来源归属：admin/agent/system',
    `runner_mode`            VARCHAR(32)  NOT NULL DEFAULT 'draft_only' COMMENT '执行模式',
    `status`                 VARCHAR(32)  NOT NULL DEFAULT 'active' COMMENT '状态：active/paused/archived/invalid',
    `last_task_id`           BIGINT       NULL COMMENT '最近一次任务ID',
    `last_result_summary`    TEXT         NULL COMMENT '最近结果摘要',
    `last_insert_count`      INT          NOT NULL DEFAULT 0 COMMENT '最近新增数量',
    `last_update_count`      INT          NOT NULL DEFAULT 0 COMMENT '最近更新数量',
    `last_unchanged_count`   INT          NOT NULL DEFAULT 0 COMMENT '最近无变化数量',
    `last_failed_count`      INT          NOT NULL DEFAULT 0 COMMENT '最近失败数量',
    `last_success_time`      DATETIME     NULL COMMENT '最近成功时间',
    `consecutive_fail_count` INT          NOT NULL DEFAULT 0 COMMENT '连续失败次数',
    `create_time`            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_gather_source_hash` (`source_hash`),
    KEY `idx_gather_source_owner_status` (`owner_type`, `status`),
    KEY `idx_gather_source_update_time` (`update_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='采集源表';
