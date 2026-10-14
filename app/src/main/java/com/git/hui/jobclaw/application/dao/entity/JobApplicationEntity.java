package com.git.hui.jobclaw.application.dao.entity;

import com.git.hui.jobclaw.application.model.ApplicationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** AI-GENERATED AIDEV-NOTE: 保存职位快照，原岗位下架后仍可跟进个人记录。 */
@Data
@Accessors(chain = true)
@Entity
@Table(name = "job_application",
        uniqueConstraints = @UniqueConstraint(name = "uk_job_application_user_job", columnNames = {"user_id", "job_id"}),
        indexes = @Index(name = "idx_job_application_user_status", columnList = "user_id,status"))
public class JobApplicationEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "job_id", nullable = false)
    private Long jobId;

    @Column(name = "company_name", nullable = false, length = 512)
    private String companyName;

    @Column(name = "position", nullable = false, columnDefinition = "TEXT")
    private String position;

    @Column(name = "job_location", nullable = false, columnDefinition = "TEXT")
    private String jobLocation;

    @Column(name = "related_link", nullable = false, columnDefinition = "TEXT")
    private String relatedLink;

    @Column(name = "job_announcement", nullable = false, columnDefinition = "TEXT")
    private String jobAnnouncement;

    @Column(name = "deadline", nullable = false, length = 512)
    private String deadline;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ApplicationStatus status;

    @Column(name = "notes", nullable = false, length = 4000)
    private String notes;

    @Column(name = "next_follow_up_date")
    private LocalDate nextFollowUpDate;

    @Column(name = "create_time", nullable = false)
    private LocalDateTime createTime;

    @Column(name = "update_time", nullable = false)
    private LocalDateTime updateTime;
}
