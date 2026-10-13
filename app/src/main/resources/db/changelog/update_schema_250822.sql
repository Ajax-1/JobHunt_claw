-- 新增优惠券
CREATE TABLE `user_interest`
(
    `id`                     bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id`                bigint unsigned NOT NULL default '0' COMMENT '用户id',
    `company_type`           varchar(128)  NOT NULL DEFAULT '' COMMENT '公司类型',
    `company_industry`           varchar(128)  NOT NULL DEFAULT '' COMMENT '所属行业',
    `job_location`           varchar(512)  NOT NULL DEFAULT '' COMMENT '工作地点',
    `recruitment_type`       varchar(512)  NOT NULL DEFAULT '' COMMENT '招聘类型',
    `recruitment_target`     varchar(128)  NOT NULL DEFAULT '' COMMENT '招聘对象',
    `position`               varchar(1024) NOT NULL DEFAULT '' COMMENT '岗位',
    `interest`                varchar(1024)  NOT NULL DEFAULT '' COMMENT '用户偏好',
    `create_time`            timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`            timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    PRIMARY KEY (`id`),
    KEY                      `ix_user_id` (`user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4  COMMENT='用户订阅偏好';

