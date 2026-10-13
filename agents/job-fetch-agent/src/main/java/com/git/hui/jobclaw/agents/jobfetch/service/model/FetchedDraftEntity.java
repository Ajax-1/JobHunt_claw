package com.git.hui.jobclaw.agents.jobfetch.service.model;

import java.util.Date;

/**
 * ai获取的草稿数据，通常需要进一步进行处理
 *
 * @date 2025/7/14
 */
public interface FetchedDraftEntity {

    /**
     * 获取草稿ID
     *
     * @return 草稿ID
     */
    Long getId();

    /**
     * 获取公司名称
     *
     * @return 公司名称
     */
    String getCompanyName();

    /**
     * 获取公司类型
     *
     * @return 公司类型，如国企、事业单位、学校、银行、私企等
     */
    String getCompanyType();

    /**
     * 获取公司行业
     *
     * @return 公司行业，如建筑、IT/互联网、机器/无人机、生物制药等
     */
    String getCompanyIndustry();

    /**
     * 获取工作地点
     *
     * @return 工作地点，多个地点用逗号分隔
     */
    String getJobLocation();

    /**
     * 获取招聘类型
     *
     * @return 招聘类型，如秋招、校招、实习、社招等，多个类型用逗号分隔
     */
    String getRecruitmentType();

    /**
     * 获取招聘对象
     *
     * @return 招聘对象，如2026年毕业生、三年以上经验等
     */
    String getRecruitmentTarget();

    /**
     * 获取岗位名称
     *
     * @return 岗位名称，多个岗位用逗号分隔
     */
    String getPosition();

    /**
     * 获取投递进度
     *
     * @return 投递进度，如进行中、已截止等
     */
    String getDeliveryProgress();

    /**
     * 获取岗位更新时间
     *
     * @return 岗位更新时间，如2026-04-18
     */
    String getLastUpdatedTime();

    /**
     * 获取投递截止时间
     *
     * @return 投递截止时间，如2026-05-01
     */
    String getDeadline();

    /**
     * 获取相关链接
     *
     * @return 相关链接，如投递链接、官网链接等，多个链接用逗号分隔
     */
    String getRelatedLink();

    /**
     * 获取招聘公告详情
     *
     * @return 招聘公告详细内容
     */
    String getJobAnnouncement();

    /**
     * 获取内推码
     *
     * @return 内推码
     */
    String getInternalReferralCode();

    /**
     * 获取备注信息
     *
     * @return 备注信息
     */
    String getRemarks();

    /**
     * 获取薪资范围
     *
     * @return 薪资范围，如15k-25k、面议等
     */
    String getSalary();

    /**
     * 获取学历要求
     *
     * @return 学历要求，如本科、硕士、博士等
     */
    String getEducation();

    /**
     * 获取工作经验要求
     *
     * @return 工作经验要求，如应届生、1-3年、3-5年等
     */
    String getExperience();

    /**
     * 获取创建时间
     *
     * @return 创建时间
     */
    Date getCreateTime();
}
