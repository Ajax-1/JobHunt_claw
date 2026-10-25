package io.github.ajax1.campusradar.job;

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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 一个岗位一行。同一来源内用 (source, externalId) 唯一定位，支撑增量更新。
 */
@Entity
@Table(name = "job_posting",
        uniqueConstraints = @UniqueConstraint(name = "uk_job_source_ext", columnNames = {"source", "externalId"}),
        indexes = {
                @Index(name = "idx_job_status_posted", columnList = "status,postedAt"),
                @Index(name = "idx_job_company", columnList = "company")
        })
public class JobPosting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 数据源编码，如 tencent、bytedance
     */
    @Column(nullable = false, length = 32)
    private String source;

    /**
     * 来源侧的岗位 ID
     */
    @Column(nullable = false, length = 128)
    private String externalId;

    @Column(nullable = false, length = 64)
    private String company;

    @Column(nullable = false, length = 256)
    private String title;

    /**
     * 职能类别，如 技术、产品、运营，各家叫法不统一，原样保存
     */
    @Column(length = 64)
    private String category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private RecruitType recruitType;

    /**
     * 毕业届别，如 2027；来源没有给出时为空
     */
    private Integer gradYear;

    /**
     * 形如 ",北京,上海,"，首尾带逗号，便于按城市精确匹配
     */
    @Column(length = 512)
    private String cities;

    /**
     * 用长文本而不是 @Lob：Hibernate 不允许对 CLOB 做 lower()，关键词检索需要大小写不敏感
     */
    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    private String description;

    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    private String requirement;

    @Column(length = 512)
    private String applyUrl;

    private Instant publishedAt;

    /**
     * 排序和「最近 N 天」筛选用的日期：有发布时间用发布时间，没有（如腾讯）用首次采到的时间
     */
    private Instant postedAt;

    private LocalDate deadline;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private JobStatus status;

    @Column(nullable = false)
    private Instant firstSeenAt;

    @Column(nullable = false)
    private Instant lastSeenAt;

    /**
     * 标题、JD、城市的摘要，用来判断岗位内容是否有变化
     */
    @Column(length = 64)
    private String contentHash;

    private Instant updatedAt;

    public Long getId() {
        return id;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getExternalId() {
        return externalId;
    }

    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }

    public String getCompany() {
        return company;
    }

    public void setCompany(String company) {
        this.company = company;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public RecruitType getRecruitType() {
        return recruitType;
    }

    public void setRecruitType(RecruitType recruitType) {
        this.recruitType = recruitType;
    }

    public Integer getGradYear() {
        return gradYear;
    }

    public void setGradYear(Integer gradYear) {
        this.gradYear = gradYear;
    }

    public String getCities() {
        return cities;
    }

    public void setCities(String cities) {
        this.cities = cities;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getRequirement() {
        return requirement;
    }

    public void setRequirement(String requirement) {
        this.requirement = requirement;
    }

    public String getApplyUrl() {
        return applyUrl;
    }

    public void setApplyUrl(String applyUrl) {
        this.applyUrl = applyUrl;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(Instant publishedAt) {
        this.publishedAt = publishedAt;
    }

    public Instant getPostedAt() {
        return postedAt;
    }

    public void setPostedAt(Instant postedAt) {
        this.postedAt = postedAt;
    }

    public LocalDate getDeadline() {
        return deadline;
    }

    public void setDeadline(LocalDate deadline) {
        this.deadline = deadline;
    }

    public JobStatus getStatus() {
        return status;
    }

    public void setStatus(JobStatus status) {
        this.status = status;
    }

    public Instant getFirstSeenAt() {
        return firstSeenAt;
    }

    public void setFirstSeenAt(Instant firstSeenAt) {
        this.firstSeenAt = firstSeenAt;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }

    public void setLastSeenAt(Instant lastSeenAt) {
        this.lastSeenAt = lastSeenAt;
    }

    public String getContentHash() {
        return contentHash;
    }

    public void setContentHash(String contentHash) {
        this.contentHash = contentHash;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
