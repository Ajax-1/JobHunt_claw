package io.github.ajax1.campusradar.mcp;

import io.github.ajax1.campusradar.collect.CompanyDirectory;
import io.github.ajax1.campusradar.collect.SourceStatus;
import io.github.ajax1.campusradar.collect.SourceStatusService;
import io.github.ajax1.campusradar.job.JobQuery;
import io.github.ajax1.campusradar.job.JobQueryService;
import io.github.ajax1.campusradar.job.JobViews;
import io.github.ajax1.campusradar.job.RecruitType;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 通过 MCP 开放给外部 Agent 的只读工具。P0 只开放岗位库，档案和投递台账在 P1、P2 加入。
 * 抛出的异常会被转成 isError=true 的工具结果，异常信息就是给 Agent 看的提示。
 */
@Component
public class JobTools {

    private final JobQueryService queryService;
    private final SourceStatusService sourceStatusService;
    private final CompanyDirectory companies;

    public JobTools(JobQueryService queryService, SourceStatusService sourceStatusService, CompanyDirectory companies) {
        this.queryService = queryService;
        this.sourceStatusService = sourceStatusService;
        this.companies = companies;
    }

    @Tool(name = "search_jobs", description = """
            在国内校招岗位库里检索在招岗位，返回摘要（不含 JD 全文）。所有条件都可以不填。
            排序：有关键词时按相关度（标题命中 > 职能类别 > JD 命中），否则按 postedAt 倒序；
            没指定公司时结果会在各公司之间轮流穿插。传了关键词时 snippet 是 JD 里的命中片段。
            需要看完整职责和任职要求时，再用 get_job 查单个岗位。""")
    public List<JobViews.Summary> searchJobs(
            @ToolParam(required = false, description = "关键词，空格分隔表示同时包含，匹配岗位名、职能类别和 JD，如 \"后端 Java\"、\"大模型 算法\"") String keyword,
            @ToolParam(required = false, description = "公司，支持简称和英文名，如 字节、ByteDance、阿里、PDD；收录了哪些公司见 list_sources") String company,
            @ToolParam(required = false, description = "工作城市，如 北京、上海、深圳") String city,
            @ToolParam(required = false, description = "招聘类型：校招 或 实习") String recruitType,
            @ToolParam(required = false, description = "毕业届别，如 2027；不少岗位没有标注届别，填了会把它们过滤掉") Integer gradYear,
            @ToolParam(required = false, description = "只看最近 N 天发布的岗位；没有发布时间的来源按首次采到的时间算") Integer sinceDays,
            @ToolParam(required = false, description = "返回条数，默认 20，最多 50") Integer limit) {
        return queryService.search(new JobQuery(keyword, resolveCompany(company), city, parseRecruitType(recruitType),
                gradYear, sinceDays, limit == null ? 20 : limit));
    }

    @Tool(name = "get_job", description = "按 search_jobs 返回的 id 查看岗位完整信息：岗位职责、任职要求、城市、官网投递链接 applyUrl")
    public JobViews.Detail getJob(@ToolParam(description = "岗位 id") long id) {
        return queryService.get(id).orElseThrow(() -> new IllegalArgumentException(
                "岗位不存在: " + id + "。请使用 search_jobs 返回的 id"));
    }

    @Tool(name = "list_sources", description = "列出岗位库覆盖的公司、每家在招岗位数和最近一次采集时间，用于判断数据是否新鲜、覆盖了哪些公司")
    public List<SourceStatus> listSources() {
        return sourceStatusService.list();
    }

    private String resolveCompany(String company) {
        if (company == null || company.isBlank()) {
            return null;
        }
        return companies.resolve(company).orElseThrow(() -> new IllegalArgumentException(
                "没有收录公司「" + company + "」。已收录：" + String.join("、", companies.companies())));
    }

    private static RecruitType parseRecruitType(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        RecruitType type = RecruitType.parse(value);
        if (type == null) {
            throw new IllegalArgumentException("recruitType 只能是 校招 或 实习，收到：" + value);
        }
        return type;
    }
}
