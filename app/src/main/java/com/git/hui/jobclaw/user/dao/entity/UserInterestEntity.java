package com.git.hui.jobclaw.user.dao.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.Date;

/**
 * 用户偏好
 *
 * @date 2025/8/21
 */
@Data
@Accessors(chain = true)
@Entity(name = "user_interest")
public class UserInterestEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    /**
     * 兴趣
     */
    @Column(name = "interest")
    private String interest;
    /**
     * 公司类型
     */
    @Column(name = "company_type")
    private String companyType;

    /**
     * 公司行业
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
     * 创建时间
     */
    @Column(name = "create_time")
    private Date createTime;


    @Column(name = "update_time")
    private Date updateTime;
}
