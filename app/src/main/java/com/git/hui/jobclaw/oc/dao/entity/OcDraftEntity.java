package com.git.hui.jobclaw.oc.dao.entity;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.git.hui.jobclaw.agents.jobfetch.service.model.FetchedDraftEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.Date;

/**
 * ai获取的草稿数据，通常需要进一步进行处理
 *
 * @date 2025/7/14
 */
@Data
@Accessors(chain = true)
@Entity(name = "draft_oc")
public class OcDraftEntity implements FetchedDraftEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    /**
     * 公司名称
     */
    @Column(name = "company_name")
    private String companyName;
    /**
     * 公司类型
     */
    @Column(name = "company_type")
    private String companyType;
    /**
     * 所属行业
     */
    @Column(name = "company_industry")
    private String companyIndustry;
    /**
     * 工作地点
     */
    @Column(name = "job_location")
    private String jobLocation;
    /**
     * 招聘类型
     */
    @Column(name = "recruitment_type")
    private String recruitmentType;
    /**
     * 招聘对象
     */
    @Column(name = "recruitment_target")
    private String recruitmentTarget;
    /**
     * 岗位
     */
    @Column(name = "position")
    private String position;
    /**
     * 投递进度
     */
    @Column(name = "delivery_progress")
    private String deliveryProgress;
    /**
     * 岗位更新时间
     */
    @Column(name = "last_updated_time")
    private String lastUpdatedTime;
    /**
     * 投递截止
     */
    @Column(name = "deadline")
    private String deadline;
    /**
     * 相关链接
     */
    @Column(name = "related_link")
    private String relatedLink;
    /**
     * 招聘公告
     */
    @Column(name = "job_announcement")
    private String jobAnnouncement;
    /**
     * 内推码
     */
    @Column(name = "internal_referral_code")
    private String internalReferralCode;
    /**
     * 备注
     */
    @Column(name = "remarks")
    private String remarks;

    /**
     * 关联采集源。
     */
    @Column(name = "source_id")
    private Long sourceId;

    /**
     * 最近一次产出/更新该草稿的采集任务。
     */
    @Column(name = "source_task_id")
    private Long sourceTaskId;

    /**
     * 执行来源：admin / agent / system。
     */
    @Column(name = "runner_type")
    private String runnerType;


    /**
     * 薪资范围
     */
    @JsonPropertyDescription("薪资范围，如 15k-25k、面议等")
    private String salary;

    /**
     * 学历要求
     */
    @JsonPropertyDescription("学历要求，如 本科、硕士、博士等")
    private String education;

    /**
     * 工作经验要求
     */
    @JsonPropertyDescription("工作经验要求，如 应届生、1-3年、3-5年等")
    private String experience;

    /**
     * 状态:
     * -1 删除
     * 0 草稿
     * 1 已发布
     */
    @Column(name = "state")
    private Integer state;
    /**
     * 0 表示这条记录已处理
     * 1 表示这条数据待处理
     */
    @Column(name = "to_process")
    private Integer toProcess;
    @Column(name = "create_time")
    private Date createTime;
    @Column(name = "update_time")
    private Date updateTime;

    /**
     * 自动将null相关字段设置为""
     */
    public void autoInitVal() {
        if (this.companyName == null) {
            this.companyName = "";
        }
        if (this.companyType == null) {
            this.companyType = "";
        }
        if (this.companyIndustry == null) {
            this.companyIndustry = "";
        }
        if (this.jobLocation == null) {
            this.jobLocation = "";
        }
        if (this.recruitmentType == null) {
            this.recruitmentType = "";
        }
        if (this.recruitmentTarget == null) {
            this.recruitmentTarget = "";
        }
        if (this.position == null) {
            this.position = "";
        }
        if (this.deliveryProgress == null) {
            this.deliveryProgress = "";
        }
        if (this.lastUpdatedTime == null) {
            this.lastUpdatedTime = "";
        }
        if (this.deadline == null) {
            this.deadline = "";
        }
        if (this.relatedLink == null) {
            this.relatedLink = "";
        }
        if (this.jobAnnouncement == null) {
            this.jobAnnouncement = "";
        }
        if (this.internalReferralCode == null) {
            this.internalReferralCode = "";
        }
        if (this.remarks == null) {
            this.remarks = "";
        }
        if (this.runnerType == null) {
            this.runnerType = "";
        }
        if (this.salary == null) {
            this.salary = "";
        }
        if (this.education == null) {
            this.education = "";
        }
        if (this.experience == null) {
            this.experience = "";
        }
        if (this.state == null) {
            this.state = 0;
        }
        if (this.toProcess == null) {
            this.toProcess = 1;
        }
        if (this.createTime == null) {
            this.createTime = new Date();
        }
        if (this.updateTime == null) {
            this.updateTime = new Date();
        }
    }
}
