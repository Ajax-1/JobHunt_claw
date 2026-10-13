package com.git.hui.jobclaw.core.preference.repository;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.experimental.Accessors;
import org.hibernate.annotations.DynamicUpdate;

import java.util.Date;

/**
 * 对于普通用户的个人模型偏好，采用持久化的方案进行存储；配置导致配置值过多的问题
 * 对于系统的模型偏好配置，沿用现有的方案 --> 直接使用配置文件的方式
 * @date 2026/4/23
 */
@Data
@Accessors(chain = true)
@DynamicUpdate
@Entity(name = "ai_user_preference")
@Table(uniqueConstraints = @jakarta.persistence.UniqueConstraint(columnNames = "user_id"))
public class AiUserPreferenceEntity {
    /**
     * 主键
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;
    /**
     * 用户id
     */
    @Column(name = "user_id")
    private Long userId;

    /**
     * 用户模型偏好
     */
    @Lob
    @Column(name = "preference")
    private String preference;
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
