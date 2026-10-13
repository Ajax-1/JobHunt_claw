package com.git.hui.jobclaw.agents.jobfetch.service.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 职位抓取任务实体
 *
 * @date 2026/4/20
 */
@Data
@Accessors(chain = true)
@Entity
@Table(name = "job_fetch_task")
public class JobFetchTaskEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 任务ID(业务ID,用于查询)
     */
    @Column(nullable = false, unique = true, length = 64)
    private String taskId;

    /**
     * 用户ID
     */
    @Column(nullable = false, length = 64)
    private String jobClawUserId;

    /**
     * 任务类型: URL/TEXT/FILE
     */
    @Column(nullable = false, length = 20)
    private String taskType;

    /**
     * 任务输入(URL或文本内容或文件路径)
     */
    @Column(name = "input_content", columnDefinition = "TEXT")
    private String inputContent;

    /**
     * 原始消息(用户的额外说明)
     */
    @Lob
    @Column(name = "origin_message", columnDefinition = "TEXT")
    private String originMessage;

    /**
     * 任务状态: PENDING/RUNNING/SUCCESS/FAILED
     */
    @Column(nullable = false, length = 20)
    private String status;

    /**
     * 提取的职位数量
     */
    @Column(name = "job_count")
    private Integer jobCount;

    /**
     * 错误信息(失败时记录)
     */
    @Column(columnDefinition = "TEXT")
    private String errorMessage;


    /**
     * 渠道
     */
    @Column(name = "channel")
    private String channel;

    /**
     * 会话id
     */
    @Column(name = "conversion_id")
    private String conversionId;
    /**
     * 创建时间
     */
    @Column(nullable = false)
    private LocalDateTime createTime;

    /**
     * 开始执行时间
     */
    private LocalDateTime startTime;

    /**
     * 完成时间
     */
    private LocalDateTime finishTime;

    /**
     * 更新时间
     */
    @Column(nullable = false)
    private LocalDateTime updateTime;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
        updateTime = LocalDateTime.now();
        if (status == null) {
            status = JobFetchTaskStatus.PENDING.name();
        }
        if (jobCount == null) {
            jobCount = 0;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
    }
}
