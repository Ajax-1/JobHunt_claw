 package com.git.hui.jobclaw.plugins.jobs;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.git.hui.jobclaw.core.apis.PageListVo;
import com.git.hui.jobclaw.core.apis.models.CommonDict;
import com.git.hui.jobclaw.core.apis.models.OcJobInfo;
import com.git.hui.jobclaw.core.apis.req.UserInterestRecommendReq;
import com.git.hui.jobclaw.core.apis.service.ICommonDictService;
import com.git.hui.jobclaw.core.apis.service.IJobSearchService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 职位库检索工具
 * 根据用户偏好信息，从正式库中智能检索最可能关心的岗位信息
 *
 * @date 2026/4/21
 */
@Slf4j
@Component
public class JobLibraryTool {

    @Autowired
    private IJobSearchService jobSearchService;

    @Autowired
    private ICommonDictService commonDictService;

    /**
     * 用户偏好信息
     */
    @Data
    public static class UserPreference {
        /**
         * 期望的公司类型列表
         */
        @JsonPropertyDescription("期望的公司类型，如：国企、事业单位、学校、银行、私企等")
        private List<String> companyTypes;

        /**
         * 期望的行业列表
         */
        @JsonPropertyDescription("期望的行业，如：IT/互联网、金融、建筑、生物制药等")
        private List<String> industries;

        /**
         * 期望的工作地点列表
         */
        @JsonPropertyDescription("期望的工作地点，如：北京、上海、深圳、杭州等")
        private List<String> locations;

        /**
         * 招聘对象
         */
        @JsonPropertyDescription("招聘对象，如：2026年毕业生、应届生、3-5年经验等")
        private String recruitmentTarget;

        /**
         * 期望的招聘类型
         */
        @JsonPropertyDescription("招聘类型，如：秋招、春招、社招、实习等")
        private List<String> recruitmentTypes;

        /**
         * 期望的岗位关键词
         */
        @JsonPropertyDescription("期望的岗位关键词，如：工程师、产品经理、运营等")
        private List<String> positions;

        /**
         * 每页数量
         */
        @JsonPropertyDescription("返回结果数量，默认10条")
        private Integer pageSize;
    }

    /**
     * 职位简要信息
     */
    @Data
    public static class JobSummary {
        private Long id;
        private String companyName;
        private String companyType;
        private String companyIndustry;
        private String position;
        private String jobLocation;
        private String recruitmentType;
        private String recruitmentTarget;
        private String deadline;
        private String relatedLink;
    }

    /**
     * 查询职位库的可用筛选项(字典数据)
     *
     * @return 可用的筛选选项列表
     */
    @Tool(description = """
            查询职位库中可用的筛选选项,帮助用户了解可以使用的筛选条件。
                        
            **触发时机**: 当用户表达以下意图时调用此工具:
            - 查询选项: "有哪些筛选条件"、"可以按什么筛选"、"支持哪些公司类型"
            - 帮助信息: "帮我看看有哪些选择"、"可选的行业有哪些"
            - 构建偏好前: 用户在设置偏好前先了解可用选项
            - 不确定时: 用户不清楚应该输入什么值
                        
            **使用场景**:
            1. 用户想了解可用的筛选维度
            2. 用户在构建偏好前需要参考
            3. 用户不确定某个字段应该填什么值
                        
            **返回内容**:
            - 公司类型列表: 国企、事业单位、学校、银行、私企等
            - 行业列表: IT/互联网、金融、建筑、生物制药等
            - 地点列表: 北京、上海、深圳、杭州等
            - 招聘对象: 2026年毕业生、应届生、有经验等
            - 招聘类型: 秋招、春招、社招、实习等
            - 岗位关键词: 工程师、产品经理、运营等
                        
            **典型对话**:
            用户: "有哪些公司类型可以选择?"
            → 调用此工具,返回所有可用的筛选项
                        
            用户: "我想找北京的职位,北京在地点列表里吗?"
            → 调用此工具,查看地点列表
            """)
    public String getAvailableFilterOptions() {
        log.info("工具调用：查询可用筛选选项");

        try {
            // 查询 app=oc 的所有字典
            List<CommonDict> dictList = commonDictService.queryByApp("oc");

            if (dictList == null || dictList.isEmpty()) {
                return "⚠️ 暂无可用的筛选选项\n\n💡 提示:\n• 请联系管理员配置字典数据";
            }

            StringBuilder sb = new StringBuilder();

            sb.append("📋 可用的筛选选项\n\n");

            // 按照相同的key，进行聚合分组
            Map<String, List<DictItemVo>> dictItemMap = new HashMap<>();
            dictList.forEach(item -> {
                DictItemVo dictItemVo = new DictItemVo(item.getKey(), item.getValue(), item.getIntro(), item.getRemark());
                dictItemMap.computeIfAbsent(item.getKey(), k -> new ArrayList<>()).add(dictItemVo);
            });

            // 然后构建消息
            dictItemMap.forEach((key, value) -> {
                sb.append("**").append(getAppDisplayName(key, value.get(0).mark())).append("**\n\n");
                value.forEach(item -> {
                    if (item.value().equals(item.intro())) {
                        sb.append("- ").append(item.value()).append("\n\n");
                    } else {
                        sb.append("- ").append(item.intro()).append(": ").append(item.value()).append("\n\n");
                    }
                });
            });

            sb.append("---\n\n");
            sb.append("💡 使用提示:\n");
            sb.append("• 使用这些选项构建您的求职偏好\n\n");
            sb.append("• 可以组合多个条件进行精确筛选\n\n");
            sb.append("• 例如: {locations:[\"北京\"], companyTypes:[\"国企\"]}\n\n");

            return sb.toString();
        } catch (Exception e) {
            log.error("查询筛选选项失败", e);
            return "❌ 查询失败，请稍后重试";
        }
    }

    public record DictItemVo(String key, String value, String intro, String mark) {
    }

    /**
     * 获取应用的显示名称
     */
    private String getAppDisplayName(String app, String defaultValue) {
        return switch (app.toLowerCase()) {
            case "CompanyTypeEnum" -> "🏢 公司类型";
            case "RecruitmentTargetEnum" -> "🎯 招聘对象";
            case "RecruitmentTypeEnum" -> "📝 招聘类型";
            default -> StringUtils.isBlank(defaultValue) ? app : defaultValue;
        };
    }

    /**
     * 根据用户偏好智能推荐职位
     *
     * @param preference 用户偏好信息
     * @return 推荐的职位列表
     */
    @Tool(description = """
            根据用户的偏好信息，从正式库中智能检索最可能关心的岗位。
                        
            **触发时机**: 当用户表达以下意图时调用此工具:
            - 查询职位: "帮我找一些...的职位"、"有哪些...的岗位"
            - 个性化推荐: "推荐适合我的职位"、"根据我的条件找工作"
            - 筛选查询: "找北京的国企"、"想看IT行业的秋招"
            - 综合搜索: "我是2026届计算机专业，想找北京的互联网公司"
                        
            **使用场景**:
            1. 用户描述自己的求职偏好
            2. 用户想要查看符合特定条件的职位
            3. 用户希望获得个性化的职位推荐
                        
            **参数说明**:
            - preference: 用户偏好对象
              * companyTypes: 公司类型列表(可选)
              * industries: 行业列表(可选)
              * locations: 工作地点列表(可选)
              * recruitmentTarget: 招聘对象(可选)
              * recruitmentTypes: 招聘类型列表(可选)
              * positions: 岗位关键词列表(可选)
              * pageSize: 返回数量，默认10
                        
            **返回内容**:
            - 匹配的职位列表(最多pageSize条)
            - 每个职位包含: 公司、岗位、地点、类型、截止时间、链接等
            - 如果无匹配结果，返回友好提示
                        
            **典型对话**:
            用户: "帮我找一些北京的国企秋招职位"
            → 调用此工具,传入 preference={locations:["北京"], companyTypes:["国企"], recruitmentTypes:["秋招"]}
                        
            用户: "我是2026届毕业生，想找IT互联网公司的研发岗位"
            → 调用此工具,传入 preference={recruitmentTarget:"2026年毕业生", industries:["IT/互联网"], positions:["研发","工程师"]}
            """)
    public String searchJobsByPreference(UserPreference preference) {
        log.info("工具调用：根据偏好检索职位, preference={}", preference);

        try {
            // 构建查询请求
            UserInterestRecommendReq req = buildRecommendRequest(preference);

            // 执行查询
            PageListVo<OcJobInfo> result = jobSearchService.recommend(req);

            if (result == null || result.getList() == null || result.getList().isEmpty()) {
                return buildEmptyResultMessage(preference);
            }

            // 转换为简要信息
            List<JobSummary> jobs = result.getList().stream()
                    .map(this::convertToSummary)
                    .collect(Collectors.toList());

            return buildSuccessMessage(jobs, result.getTotal(), preference.getPageSize(), preference);
        } catch (Exception e) {
            log.error("检索职位失败", e);
            return "❌ 检索失败，请稍后重试\n\n💡 提示:\n• 请检查网络连接\n• 可以稍后再试";
        }
    }

    /**
     * 构建推荐请求
     */
    private UserInterestRecommendReq buildRecommendRequest(UserPreference preference) {
        UserInterestRecommendReq req = new UserInterestRecommendReq();

        if (preference != null) {
            req.setCompanyTypeList(preference.getCompanyTypes());
            req.setCompanyIndustryList(preference.getIndustries());
            req.setJobLocationList(preference.getLocations());
            req.setRecruitmentTarget(preference.getRecruitmentTarget());
            req.setRecruitmentTypeList(preference.getRecruitmentTypes());
            req.setPositionList(preference.getPositions());
            req.setPage(1);
            req.setSize(preference.getPageSize() != null ? preference.getPageSize() : 10);
        } else {
            req.setPage(1);
            req.setSize(10);
        }

        return req;
    }

    /**
     * 转换为简要信息
     */
    private JobSummary convertToSummary(OcJobInfo entity) {
        JobSummary summary = new JobSummary();
        summary.setId(entity.getId());
        summary.setCompanyName(entity.getCompanyName());
        summary.setCompanyType(entity.getCompanyType());
        summary.setCompanyIndustry(entity.getCompanyIndustry());
        summary.setPosition(entity.getPosition());
        summary.setJobLocation(entity.getJobLocation());
        summary.setRecruitmentType(entity.getRecruitmentType());
        summary.setRecruitmentTarget(entity.getRecruitmentTarget());
        summary.setDeadline(entity.getDeadline());
        summary.setRelatedLink(entity.getRelatedLink());
        return summary;
    }

    /**
     * 构建成功消息
     */
    private String buildSuccessMessage(List<JobSummary> jobs, long total, int pageSize, UserPreference preference) {
        StringBuilder sb = new StringBuilder();

        sb.append(String.format("✅ 找到 %d 个匹配职位 (共 %d 个)\n\n", jobs.size(), total));

        // 显示筛选条件
        if (preference != null && hasFilterConditions(preference)) {
            sb.append("🔍 **筛选条件**:\n\n");
            if (preference.getCompanyTypes() != null && !preference.getCompanyTypes().isEmpty()) {
                sb.append(String.format("  • 公司类型: %s\n\n", String.join(", ", preference.getCompanyTypes())));
            }
            if (preference.getIndustries() != null && !preference.getIndustries().isEmpty()) {
                sb.append(String.format("  • 行业: %s\n\n", String.join(", ", preference.getIndustries())));
            }
            if (preference.getLocations() != null && !preference.getLocations().isEmpty()) {
                sb.append(String.format("  • 地点: %s\n\n", String.join(", ", preference.getLocations())));
            }
            if (preference.getRecruitmentTarget() != null && !preference.getRecruitmentTarget().isBlank()) {
                sb.append(String.format("  • 招聘对象: %s\n\n", preference.getRecruitmentTarget()));
            }
            if (preference.getRecruitmentTypes() != null && !preference.getRecruitmentTypes().isEmpty()) {
                sb.append(String.format("  • 招聘类型: %s\n\n", String.join(", ", preference.getRecruitmentTypes())));
            }
            if (preference.getPositions() != null && !preference.getPositions().isEmpty()) {
                sb.append(String.format("  • 岗位关键词: %s\n\n", String.join(", ", preference.getPositions())));
            }
            sb.append("\n");
        }

        sb.append("---\n\n");
        sb.append("📋 **职位列表**:\n\n");

        for (int i = 0; i < jobs.size(); i++) {
            JobSummary job = jobs.get(i);
            sb.append(String.format("%d. **%s** - %s\n\n", i + 1, job.getCompanyName(), job.getPosition()));
            sb.append(String.format("   📍 地点: %s\n\n", job.getJobLocation() != null ? job.getJobLocation() : "-"));
            sb.append(String.format("   🏢 类型: %s | %s\n\n",
                    job.getCompanyType() != null ? job.getCompanyType() : "-",
                    job.getCompanyIndustry() != null ? job.getCompanyIndustry() : "-"));
            sb.append(String.format("   🎯 对象: %s\n\n", job.getRecruitmentTarget() != null ? job.getRecruitmentTarget() : "-"));
            if (job.getDeadline() != null && !job.getDeadline().isBlank()) {
                sb.append(String.format("   ⏰ 截止: %s\n\n", job.getDeadline()));
            }
            if (job.getRelatedLink() != null && !job.getRelatedLink().isBlank()) {
                sb.append(String.format("   🔗 链接: %s\n\n", job.getRelatedLink()));
            }
            sb.append("\n");
        }

        if (total > pageSize) {
            sb.append("---\n\n");
            sb.append(String.format("💡 提示:\n• 还有 %d 个职位未显示\n\n", total - pageSize));
            sb.append("• 可以调整筛选条件或增加pageSize查看更多\n\n");
        } else {
            sb.append("---\n\n");
            sb.append("💡 提示:\n• 点击链接可直接查看详情和投递\n\n");
            sb.append("• 如需更精确的推荐，请提供更多偏好信息\n\n");
        }

        return sb.toString();
    }

    /**
     * 判断是否有筛选条件
     */
    private boolean hasFilterConditions(UserPreference preference) {
        if (preference == null) {
            return false;
        }
        return (preference.getCompanyTypes() != null && !preference.getCompanyTypes().isEmpty())
                || (preference.getIndustries() != null && !preference.getIndustries().isEmpty())
                || (preference.getLocations() != null && !preference.getLocations().isEmpty())
                || (preference.getRecruitmentTarget() != null && !preference.getRecruitmentTarget().isBlank())
                || (preference.getRecruitmentTypes() != null && !preference.getRecruitmentTypes().isEmpty())
                || (preference.getPositions() != null && !preference.getPositions().isEmpty());
    }

    /**
     * 构建空结果消息
     */
    private String buildEmptyResultMessage(UserPreference preference) {
        StringBuilder sb = new StringBuilder();

        sb.append("⚠️ 暂未找到匹配的职位\n\n");

        if (preference != null) {
            sb.append("📋 您的筛选条件:\n");
            if (preference.getCompanyTypes() != null && !preference.getCompanyTypes().isEmpty()) {
                sb.append(String.format("  • 公司类型: %s\n", String.join(", ", preference.getCompanyTypes())));
            }
            if (preference.getIndustries() != null && !preference.getIndustries().isEmpty()) {
                sb.append(String.format("  • 行业: %s\n", String.join(", ", preference.getIndustries())));
            }
            if (preference.getLocations() != null && !preference.getLocations().isEmpty()) {
                sb.append(String.format("  • 地点: %s\n", String.join(", ", preference.getLocations())));
            }
            if (preference.getPositions() != null && !preference.getPositions().isEmpty()) {
                sb.append(String.format("  • 岗位: %s\n", String.join(", ", preference.getPositions())));
            }
            sb.append("\n");
        }

        sb.append("💡 建议:\n");
        sb.append("• 尝试放宽筛选条件(如减少地点限制)\n");
        sb.append("• 使用更通用的岗位关键词\n");
        sb.append("• 可以查看所有最新职位\n");

        return sb.toString();
    }
}
