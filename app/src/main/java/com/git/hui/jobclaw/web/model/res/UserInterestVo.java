package com.git.hui.jobclaw.web.model.res;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * @date 2025/8/21
 */
public record UserInterestVo(
        String interest,
        /**
         * 公司类型
         */
        @JsonPropertyDescription("公司类型：如央企、国企、民企")
        String companyType,

        /**
         * 公司行业
         */
        @JsonPropertyDescription("公司所属的行业类型：如数字能源/新能源, 工程承包/建筑施工, 科技, 互联网, 教培, 教育, 学校等")
        String companyIndustry,

        /**
         * 工作地点
         */
        @JsonPropertyDescription("工作地点，通常为地址，如：武汉、北京、全国")
        String jobLocation,

        /**
         * 招聘类型
         */
        @JsonPropertyDescription("招聘类型：如校招、秋招、春招、实习")
        String recruitmentType,
        /**
         * 招聘对象
         */
        @JsonPropertyDescription("招聘对象：如2026年毕业生, 2025年毕业生")
        String recruitmentTarget,
        /**
         * 岗位
         */
        @JsonPropertyDescription("岗位，如自动驾驶算法 AI数据工程 AI安全/隐私保护 大模型应用 通用软件开发 嵌入式软件开发 操作系统与编译器开发 数据")
        String position) {
}
