package com.git.hui.jobclaw.core.apis.models;

import java.util.Date;

/**
 * 正式库职位信息接口
 *
 * @date 2026/4/21
 */
public interface OcJobInfo {
    /**
     * 获取职位ID
     *
     * @return 职位ID
     */
    Long getId();

    /**
     * 获取关联的草稿ID
     *
     * @return 草稿ID
     */
    Long getDraftId();

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
     * @return 岗位更新时间
     */
    Date getLastUpdatedTime();

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
     * 获取职位状态
     *
     * @return 状态：-1删除、0草稿、1已发布
     */
    Integer getState();

    /**
     * 获取创建时间
     *
     * @return 创建时间
     */
    Date getCreateTime();

    /**
     * 获取更新时间
     *
     * @return 更新时间
     */
    Date getUpdateTime();
}
