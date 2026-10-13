CREATE TABLE `mcp_client`
(
    `id`          bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id`     bigint unsigned NOT NULL default '0' COMMENT '用户id',
    `session_id`  varchar(128) NOT NULL DEFAULT '' COMMENT '会话id',
    `init_info`   varchar(512) NOT NULL DEFAULT '' COMMENT '初始化上下文',
    `notify_info` varchar(512) NOT NULL DEFAULT '' COMMENT '初始化上下文',
    `create_time` timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY           `ix_user_id` (`user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4  COMMENT='McpClient会话持久化';

