package io.github.ajax1.campusradar.push;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/**
 * 推送过的岗位，同一个岗位只推一次
 */
@Entity
@Table(name = "push_record",
        uniqueConstraints = @UniqueConstraint(name = "uk_push_profile_job", columnNames = {"profileId", "jobId"}))
public class PushRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32)
    private String profileId;

    @Column(nullable = false)
    private Long jobId;

    private int score;

    @Column(nullable = false)
    private Instant pushedAt;

    protected PushRecord() {
    }

    public PushRecord(String profileId, Long jobId, int score, Instant pushedAt) {
        this.profileId = profileId;
        this.jobId = jobId;
        this.score = score;
        this.pushedAt = pushedAt;
    }

    public Long getJobId() {
        return jobId;
    }

    public Instant getPushedAt() {
        return pushedAt;
    }
}
