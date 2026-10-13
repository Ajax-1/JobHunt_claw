package com.git.hui.jobclaw.gather.dao.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import lombok.Data;
import lombok.experimental.Accessors;
import org.hibernate.annotations.DynamicUpdate;

import java.util.Date;

/**
 * 可复用的采集来源资产。
 */
@Data
@Accessors(chain = true)
@DynamicUpdate
@Entity(name = "gather_source")
public class GatherSourceEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "type")
    private Integer type;

    @Column(name = "title")
    private String title;

    @Lob
    @Column(name = "content")
    private String content;

    @Column(name = "source_hash")
    private String sourceHash;

    @Column(name = "content_hash")
    private String contentHash;

    @Column(name = "version")
    private Integer version;

    @Column(name = "model")
    private String model;

    @Column(name = "owner_type")
    private String ownerType;

    @Column(name = "runner_mode")
    private String runnerMode;

    @Column(name = "status")
    private String status;

    @Column(name = "last_task_id")
    private Long lastTaskId;

    @Lob
    @Column(name = "last_result_summary")
    private String lastResultSummary;

    @Column(name = "last_insert_count")
    private Integer lastInsertCount;

    @Column(name = "last_update_count")
    private Integer lastUpdateCount;

    @Column(name = "last_unchanged_count")
    private Integer lastUnchangedCount;

    @Column(name = "last_failed_count")
    private Integer lastFailedCount;

    @Column(name = "last_success_time")
    private Date lastSuccessTime;

    @Column(name = "consecutive_fail_count")
    private Integer consecutiveFailCount;

    @Column(name = "create_time")
    private Date createTime;

    @Column(name = "update_time")
    private Date updateTime;
}
