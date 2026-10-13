package com.git.hui.jobclaw.gather.model;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * 采集到的oc草稿数据
 *
 * @date 2025/7/14
 */
public record GatherOcDraftBo(
        @JsonPropertyDescription("公司名称，如 暴富有限公司")
        String companyName,         // 公司名称
        @JsonPropertyDescription("公司类型，如 国企，事业单位，学校，银行，私企等")
        String companyType,         // 公司类型
        @JsonPropertyDescription("公司行业，如 建筑，IT/互联网，机器二/无人机，生物制药/生命科学等")
        String companyIndustry,     // 公司行业
        @JsonPropertyDescription("工作地点，如 云南、武汉、北京、深圳、全国等")
        String jobLocation,         // 工作地点
        @JsonPropertyDescription("招聘类型，如 秋招，校招，实习，社招，秋招提前批等")
        String recruitmentType,     // 招聘类型
        @JsonPropertyDescription("招聘对象，如 2026年毕业生、三年以上经验等")
        String requirementTarget,      // 招聘对象
        @JsonPropertyDescription("岗位，如 研发工程师/工艺工程师/检测工程师/电气工程师/安全工程师/技工")
        String position,            // 岗位(大都不限专业)
        @JsonPropertyDescription("投递进度")
        String deliveryProgress,   // 投递进度
        @JsonPropertyDescription("最后更新时间，如 2025年08月14日")
        String lastUpdatedTime,     // 更新时间
        @JsonPropertyDescription("截止时间，如 招满为止、2025年10月13日")
        String deadline,            // 投递截止
        @JsonPropertyDescription("招聘相关的链接，通常是http格式的链接")
        String relatedLink,         // 相关链接
        @JsonPropertyDescription("招聘相关的公告，通常是http格式的链接地址")
        String jobAnnouncement,     // 招聘公告
        @JsonPropertyDescription("内推码")
        String internalReferralCode,// 内推码
        @JsonPropertyDescription("备注")
        String remarks,             // 备注
        /**
         * 薪资范围
         */
        @JsonPropertyDescription("薪资范围，如 15k-25k、面议等 没有则为null")
        String salary,
        /**
         * 学历要求
         */
        @JsonPropertyDescription("学历要求，如 本科、硕士、博士等 没有则为null")
        String education,

        /**
         * 工作经验要求
         */
        @JsonPropertyDescription("工作经验要求，如 应届生、1-3年、3-5年等 没有则为null")
        String experience
) {
}
