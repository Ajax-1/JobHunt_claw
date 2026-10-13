package com.git.hui.jobclaw.agents.jobfetch.service.model;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.Data;

import java.io.Serializable;

/**
 * 职位信息模型
 * 统一的职位数据结构，用于爬虫和提取器的输出
 *
 * @date 2026/4/18
 */
@Data
public class FetchedJobInfo implements Serializable {

    /**
     * 公司名称
     */
    @JsonPropertyDescription("公司名称，如 暴富有限公司")
    private String companyName;

    /**
     * 公司类型
     */
    @JsonPropertyDescription("公司类型，如 国企、事业单位、学校、银行、私企等")
    private String companyType;

    /**
     * 公司行业
     */
    @JsonPropertyDescription("公司行业，如 建筑、IT/互联网、机器/无人机、生物制药/生命科学等")
    private String companyIndustry;

    /**
     * 工作地点
     */
    @JsonPropertyDescription("工作地点，如 云南、武汉、北京、深圳、全国等，多个地点用逗号分隔")
    private String jobLocation;

    /**
     * 招聘类型
     */
    @JsonPropertyDescription("招聘类型，如 秋招、校招、实习、社招、秋招提前批等，多个类型用逗号分隔")
    private String recruitmentType;

    /**
     * 招聘对象
     */
    @JsonPropertyDescription("招聘对象，如 2026年毕业生、三年以上经验等")
    private String recruitmentTarget;

    /**
     * 招聘对象（别名，兼容 GatherOcDraftBo.requirementTarget）
     * AIDEV-NOTE: 此字段用于兼容旧系统的 requirementTarget 命名
     */
    @JsonPropertyDescription("招聘对象（别名），如 2026年毕业生、三年以上经验等")
    private String requirementTarget;

    /**
     * 岗位
     */
    @JsonPropertyDescription("岗位，如 研发工程师/工艺工程师/检测工程师/电气工程师/安全工程师/技工，多个岗位用逗号分隔")
    private String position;

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
     * 投递进度
     */
    @JsonPropertyDescription("投递进度，如 进行中、已截止等")
    private String deliveryProgress;

    /**
     * 岗位更新时间
     */
    @JsonPropertyDescription("岗位更新时间，如 2026-04-18")
    private String lastUpdatedTime;

    /**
     * 投递截止时间
     */
    @JsonPropertyDescription("投递截止时间，如 2026-05-01")
    private String deadline;

    /**
     * 相关链接
     */
    @JsonPropertyDescription("相关链接，如 投递链接、官网链接等，多个链接用逗号分隔")
    private String relatedLink;

    /**
     * 招聘公告详情
     */
    @JsonPropertyDescription("招聘公告详细内容")
    private String jobAnnouncement;

    /**
     * 内推码
     */
    @JsonPropertyDescription("内推码")
    private String internalReferralCode;

    /**
     * 备注
     */
    @JsonPropertyDescription("备注信息")
    private String remarks;

    /**
     * 信息来源
     */
    @JsonPropertyDescription("信息来源，如 网址、文件名称、图片等")
    private String source;

    /**
     * 抓取/提取时间
     */
    private String fetchTime;

    /**
     * 原始数据（可选，用于调试）
     */
    private String rawData;

    /**
     * 验证职位信息是否有效
     *
     * @return true 如果至少包含公司名称或岗位名称
     */
    public boolean isValid() {
        return (companyName != null && !companyName.isBlank())
                || (position != null && !position.isBlank());
    }

    /**
     * 获取招聘对象（优先使用 recruitmentTarget，其次 requirementTarget）
     *
     * @return 招聘对象
     */
    public String getRecruitmentTarget() {
        if (recruitmentTarget != null && !recruitmentTarget.isBlank()) {
            return recruitmentTarget;
        }
        return requirementTarget;
    }

    /**
     * 设置招聘对象（同时设置两个字段以保持兼容）
     *
     * @param target 招聘对象
     */
    public void setRecruitmentTarget(String target) {
        this.recruitmentTarget = target;
        this.requirementTarget = target;
    }

    /**
     * 设置招聘对象（别名方法，兼容 GatherOcDraftBo）
     *
     * @param target 招聘对象
     */
    public void setRequirementTarget(String target) {
        this.requirementTarget = target;
        this.recruitmentTarget = target;
    }

    /**
     * 获取简化的职位描述
     *
     * @return 格式：公司名称 - 岗位 - 地点
     */
    public String getSimpleDescription() {
        StringBuilder sb = new StringBuilder();
        if (companyName != null && !companyName.isBlank()) {
            sb.append(companyName);
        }
        if (position != null && !position.isBlank()) {
            if (sb.length() > 0) sb.append(" - ");
            sb.append(position);
        }
        if (jobLocation != null && !jobLocation.isBlank()) {
            if (sb.length() > 0) sb.append(" - ");
            sb.append(jobLocation);
        }
        return sb.toString();
    }
}
