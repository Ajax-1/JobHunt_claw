package io.github.ajax1.campusradar.mcp;

import io.github.ajax1.campusradar.match.JobMatcher;
import io.github.ajax1.campusradar.match.MatchResult;
import io.github.ajax1.campusradar.profile.ProfileService;
import io.github.ajax1.campusradar.profile.ProfileView;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * 求职档案和按档案匹配。档案只存求职偏好和能力关键词，不要写入姓名、手机号、身份证号等个人信息。
 */
@Component
public class ProfileTools {

    private final ProfileService profiles;
    private final JobMatcher matcher;

    public ProfileTools(ProfileService profiles, JobMatcher matcher) {
        this.profiles = profiles;
        this.matcher = matcher;
    }

    @Tool(name = "get_profile", description = "查看当前的求职档案：届别、学历、专业、目标方向、技能、意向城市、招聘类型、偏好与排除的公司、排除关键词、经历简述")
    public ProfileView getProfile() {
        return profiles.view();
    }

    @Tool(name = "update_profile", description = """
            更新求职档案，只改传了的字段：不传或传 null 表示不改，列表传 [] 表示清空。
            targetRoles 和 skills 决定匹配质量，至少填一项才能用 match_jobs。
            不要写入姓名、手机号、身份证号等个人信息；summary 只写经历要点。""")
    public ProfileView updateProfile(
            @ToolParam(required = false, description = "毕业届别，四位年份，如 2027") Integer gradYear,
            @ToolParam(required = false, description = "学历：本科、硕士、博士") String degree,
            @ToolParam(required = false, description = "专业，如 计算机科学与技术") String major,
            @ToolParam(required = false, description = "目标岗位方向，如 [\"后端\", \"Java\", \"大模型应用\"]") List<String> targetRoles,
            @ToolParam(required = false, description = "技能关键词，如 [\"Java\", \"Spring\", \"Redis\", \"MySQL\"]") List<String> skills,
            @ToolParam(required = false, description = "意向城市，按偏好排序，如 [\"北京\", \"上海\"]；[] 表示不限") List<String> cities,
            @ToolParam(required = false, description = "招聘类型：[\"校招\"]、[\"实习\"] 或两者；[] 表示都要") List<String> recruitTypes,
            @ToolParam(required = false, description = "偏好公司，支持简称，如 [\"字节\", \"阿里\"]") List<String> preferredCompanies,
            @ToolParam(required = false, description = "不想看的公司") List<String> excludedCompanies,
            @ToolParam(required = false, description = "岗位标题里出现就排除的词，如 [\"销售\", \"客服\"]") List<String> excludeKeywords,
            @ToolParam(required = false, description = "经历简述（项目、实习要点），之后定制简历时使用") String summary) {
        return profiles.update(new ProfileView(gradYear, degree, major, targetRoles, skills, cities, recruitTypes,
                preferredCompanies, excludedCompanies, excludeKeywords, summary, null));
    }

    @Tool(name = "match_jobs", description = """
            按求职档案给在招岗位打分（0–100），返回最匹配的岗位和每一分的理由。
            打分构成：岗位方向 40、技能 30、城市 10、偏好公司 10、新鲜度 10；招聘类型、届别、排除公司、排除关键词、意向城市是硬过滤。
            看中某个岗位后用 get_job 查看完整 JD。""")
    public List<MatchResult> matchJobs(
            @ToolParam(required = false, description = "返回条数，默认 10，最多 50") Integer limit,
            @ToolParam(required = false, description = "同一家公司最多出现几个，默认 2") Integer maxPerCompany) {
        int n = limit == null ? 10 : Math.clamp(limit, 1, 50);
        return matcher.match(profiles.current(), n, maxPerCompany == null ? 2 : maxPerCompany, Set.of());
    }
}
