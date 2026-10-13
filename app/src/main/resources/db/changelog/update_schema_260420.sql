-- 职位抓取任务表
CREATE TABLE IF NOT EXISTS job_fetch_task (
                                              id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                              task_id VARCHAR(64) NOT NULL UNIQUE COMMENT '任务ID(业务ID)',
    job_claw_user_id VARCHAR(64) NOT NULL COMMENT '用户ID',
    task_type VARCHAR(20) NOT NULL COMMENT '任务类型: URL/TEXT/FILE',
    input_content TEXT COMMENT '任务输入内容',
    origin_message TEXT COMMENT '原始消息',
    status VARCHAR(20) NOT NULL COMMENT '任务状态: PENDING/RUNNING/SUCCESS/FAILED',
    job_count INT NOT NULL DEFAULT 0 COMMENT '提取的职位数量',
    channel varchar(50) not null default '' comment '渠道',
    conversion_id varchar(50) not null default '' comment '转换id',
    error_message TEXT COMMENT '错误信息',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    start_time TIMESTAMP NULL COMMENT '开始执行时间',
    finish_time TIMESTAMP NULL COMMENT '完成时间',
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    INDEX idx_user_task (job_claw_user_id, task_id),
    INDEX idx_status (status),
    INDEX idx_create_time (create_time)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='职位抓取任务表';