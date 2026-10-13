package com.git.hui.jobclaw.agents.jobfetch;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.git.hui.jobclaw.agents.jobfetch.service.JobFetchService;
import com.git.hui.jobclaw.agents.jobfetch.service.JobInfoPersistService;
import com.git.hui.jobclaw.agents.jobfetch.service.model.FetchedDraftEntity;
import com.git.hui.jobclaw.agents.jobfetch.service.model.FetchedJobInfo;
import com.git.hui.jobclaw.agents.jobfetch.service.model.JobFetchTaskResponse;
import com.git.hui.jobclaw.core.agent.models.UserConversationInfo;
import com.git.hui.jobclaw.core.channel.ChannelReceiveMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 *
 * @date 2026/4/21
 */
@Slf4j
@Component
public class JobAgentTools {

    private final JobFetchService jobFetchService;

    public JobAgentTools(JobFetchService jobFetchService) {
        this.jobFetchService = jobFetchService;
    }

    @Tool(description = """
            从网站URL爬取职位信息,自动创建异步任务。
                        
            **触发时机**: 当用户提供URL链接时调用此工具:
            - 直接提供URL: "https://xxx.com/jobs"、"从这个链接抓取"
            - 明确请求: "帮我爬取这个网址"、"抓取这个链接的职位"
            - 多URL: "抓取这几个链接: url1, url2"
            - 指定平台: "从BOSS直聘抓取"、"拉勾网的职位"
                        
            **使用场景**:
            1. 用户分享招聘网站链接
            2. 用户要求从特定网址抓取职位
            3. 批量抓取多个招聘页面
                        
            **参数说明**:
            - url: 招聘网站的完整URL地址
            - 支持主流平台: BOSS直聘、智联招聘、前程无忧、拉勾网等
            - 如果用户提供多个URL,需要分别调用此工具
                        
            **返回内容**:
            - 任务ID: 用于后续查询进度
            - 任务状态: PENDING/RUNNING/SUCCESS/FAILED
            - 操作提示: 如何查询进度、预计等待时间
                        
            **注意事项**:
            - 此工具会创建异步任务,不会立即返回结果
            - 任务完成后会主动推送通知
            - 用户可以通过 /task <任务ID> 查询进度
            - 如果URL无效或无法访问,任务会失败
                        
            **典型对话**:
            用户: "帮我从这个链接抓取职位: https://example.com/jobs"
            → 调用此工具,传入 url="https://example.com/jobs"
                        
            用户: "抓取BOSS直聘的这个页面"
            → 需要从上下文或用户输入中提取URL,然后调用此工具
            """,
            returnDirect = true)
    public String fetchJobsFromUrl(
            @JsonPropertyDescription("招聘网站的URL地址，必须是完整的http/https链接")
            String url,
            ToolContext toolContext) {
        log.info("工具调用：从 URL爬取职位信息 - {}", url);
        ChannelReceiveMessage msg = (ChannelReceiveMessage) toolContext.getContext().get("msg");
        UserConversationInfo userConversationInfo = (UserConversationInfo) toolContext.getContext().get("user");

        JobFetchTaskResponse taskResponse = jobFetchService.fetchFromUrl(userConversationInfo, url, msg);
        return buildTaskCreatedMessage(taskResponse, "网页");
    }

    @Tool(description = """
            从文本内容、文件或图片中提取职位信息,自动创建异步任务。
                        
            **触发时机**: 当用户提供以下类型的内容时调用此工具:
            - 文本内容: 粘贴的招聘信息、招聘启事文本
            - 文件上传: PDF、Word、Excel、CSV等包含招聘信息的文件
            - 图片上传: 招聘海报截图、招聘广告图片(支持OCR)
            - 混合内容: 既有文本又有附件
                        
            **使用场景**:
            1. 用户粘贴招聘文本并要求提取
            2. 用户上传包含招聘信息的文档
            3. 用户发送招聘海报图片
            4. 从聊天记录中提取职位信息
                        
            **参数说明**:
            - text: 包含职位信息的文本内容
              * 如果用户提供的是纯文本,传入文本内容
              * 如果用户上传了文件,text可以为null或空字符串
            - path: 本地文件路径(由系统自动检测附件)
              * 通常不需要手动传入,系统会自动检测
              * 如果有多个附件,取第一个
                        
            **返回内容**:
            - 任务ID: 用于后续查询进度
            - 任务状态: PENDING/RUNNING/SUCCESS/FAILED
            - 来源类型: 文本/文件/图片
            - 操作提示: 如何查询进度
                        
            **注意事项**:
            - 此工具会创建异步任务,不会立即返回结果
            - 系统会自动检测用户上传的附件
            - 如果是文本提取,text参数必填,path可为null
            - 如果是文件提取,path由系统自动填入,text可为null
            - 任务完成后会主动推送通知
            - 支持的文件格式: PDF、Word、Excel、CSV、图片等
                        
            **典型对话**:
            用户: "从这段文字中提取职位: [粘贴的文本]"
            → 调用此工具,传入 text="[粘贴的文本]", path=null
                        
            用户: [上传PDF文件] "分析这个文件中的招聘信息"
            → 调用此工具,传入 text=null, path=系统自动检测的文件路径
                        
            用户: [上传图片] "识别这张招聘海报"
            → 调用此工具,传入 text=null, path=系统自动检测的图片路径
            """,
            returnDirect = true)
    public String extractJobsFromTextOrLocalFile(
            @JsonPropertyDescription("包含职位信息的文本内容，当从文件中解析职位信息时，text应该为null或空字符串")
            String text,
            @JsonPropertyDescription("包含职位信息的本地文件路径，由系统自动检测附件，通常无需手动传入")
            String path,
            ToolContext toolContext) {
        log.info("工具调用：从文本提取职位信息，文本: {}, 文件: {}", text == null ? "-" : text.length(), path);
        ChannelReceiveMessage msg = (ChannelReceiveMessage) toolContext.getContext().get("msg");
        UserConversationInfo userConversationInfo = (UserConversationInfo) toolContext.getContext().get("user");

        JobFetchTaskResponse taskResponse = jobFetchService.fetchFromTextOrLocalFile(userConversationInfo, text, path, msg);
        String sourceType = (text != null && !text.isBlank()) ? "文本" : "文件/图片";
        var ans = buildTaskCreatedMessage(taskResponse, sourceType);
        log.info("返回结果: {}", ans);
        return ans;
    }

    @Autowired
    private JobInfoPersistService jobInfoPersistService;

    @Tool(description = """
            查询待审核的职位草稿列表,供管理员审查。
                        
            **触发时机**: 当用户表达以下意图时调用此工具:
            - 查询草稿: "查看草稿"、"待审核职位"、"有哪些草稿"
            - 审查需求: "我要审核"、"检查新职位"、"看看新抓取的"
            - 发布前确认: "发布前先看一眼"、"有哪些需要确认"
            - 定期审查: "今天的草稿"、"最近的职位"
                        
            **使用场景**:
            1. 管理员想查看新抓取的职位草稿
            2. 发布前需要审查草稿内容
            3. 定期检查待处理的职位信息
                        
            **参数说明**:
            - size: 要查询的草稿数量,建议1-5条
            - 系统会自动限制最多返回2条,避免信息过载
            - 如果用户说"全部",传入较大的数字如10
                        
            **返回内容**:
            - 每条草稿包含: ID、公司、岗位、地点、类型、薪资、学历等
            - 提供操作提示: 如何修改、如何发布
                        
            **后续操作**:
            - 用户可以使用 updateDraftJobInfo 修改某条草稿
            - 用户可以使用 publishDraftsToOfficial 发布选中的草稿
            - 如果用户说"发布"、"确认"等,应调用发布工具
            """,
            returnDirect = true)
    public String getToPublishDraftJobInfo(
            @JsonPropertyDescription("需要查询的条数， 如 1")
            int size,
            ToolContext toolContext) {
        log.info("工具调用：查询待发布草稿, size={}", size);

        // 为了简化操作，最多只返回2条
        List<FetchedDraftEntity> drafts = jobInfoPersistService.listToBePublished(Math.min(size, 2));

        if (drafts == null || drafts.isEmpty()) {
            return "✅ 暂无待审核的职位草稿\n\n💡 提示:\n\n• 当前没有新抓取的职位信息\n\n• 可以发送URL或文件来抓取新的职位";
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("📋 待审核职位草稿 (共 %d 条)\n\n", drafts.size()));

        for (int i = 0; i < drafts.size(); i++) {
            FetchedDraftEntity draft = drafts.get(i);
            sb.append(String.format("%d. **ID: %d**\n\n", i + 1, draft.getId()));

            // 基本信息
            sb.append("📌 **基本信息**:\n\n");
            sb.append(String.format("   • 公司: %s\n\n", draft.getCompanyName() != null ? draft.getCompanyName() : "-"));
            sb.append(String.format("   • 类型: %s\n\n", draft.getCompanyType() != null && !draft.getCompanyType().isBlank() ? draft.getCompanyType() : "-"));
            sb.append(String.format("   • 行业: %s\n\n",
                    draft.getCompanyIndustry() != null && !draft.getCompanyIndustry().isBlank() ? draft.getCompanyIndustry() : "-"));
            sb.append("\n\n");

            // 职位信息
            sb.append("💼 **职位信息**:\n\n");
            sb.append(String.format("   • 岗位: %s\n\n", draft.getPosition() != null ? draft.getPosition() : "-"));
            sb.append(String.format("   • 地点: %s\n\n", draft.getJobLocation() != null ? draft.getJobLocation() : "-"));
            sb.append(String.format("   • 薪资: %s\n\n", draft.getSalary() != null && !draft.getSalary().isBlank() ? draft.getSalary() : "-"));
            sb.append("\n\n");

            // 招聘要求
            sb.append("🎯 **招聘要求**:\n\n");
            sb.append(String.format("   • 类型: %s\n\n", draft.getRecruitmentType() != null ? draft.getRecruitmentType() : "-"));
            sb.append(String.format("   • 对象: %s\n\n", draft.getRecruitmentTarget() != null ? draft.getRecruitmentTarget() : "-"));
            sb.append(String.format("   • 学历: %s\n\n", draft.getEducation() != null && !draft.getEducation().isBlank() ? draft.getEducation() : "-"));
            sb.append(String.format("   • 经验: %s\n\n", draft.getExperience() != null && !draft.getExperience().isBlank() ? draft.getExperience() : "-"));
            sb.append("\n\n");

            // 时间信息
            sb.append("📅 **时间信息**:\n\n");
            sb.append(String.format("   • 更新: %s\n\n",
                    draft.getLastUpdatedTime() != null && !draft.getLastUpdatedTime().isBlank() ? draft.getLastUpdatedTime() : "-"));
            sb.append(String.format("   • 截止: %s\n\n", draft.getDeadline() != null && !draft.getDeadline().isBlank() ? draft.getDeadline() : "-"));
            sb.append(String.format("   • 进度: %s\n\n",
                    draft.getDeliveryProgress() != null && !draft.getDeliveryProgress().isBlank() ? draft.getDeliveryProgress() : "-"));
            sb.append("\n\n");

            // 其他信息
            StringBuilder otherInfo = new StringBuilder();
            if (draft.getRelatedLink() != null && !draft.getRelatedLink().isBlank()) {
                otherInfo.append(String.format("   • 链接: %s\n\n", draft.getRelatedLink()));
            } else {
                otherInfo.append("   • 链接: 无%s\n\n");
            }
            if (draft.getInternalReferralCode() != null && !draft.getInternalReferralCode().isBlank()) {
                otherInfo.append(String.format("   • 内推码: %s\n\n", draft.getInternalReferralCode()));
            } else {
                otherInfo.append("   • 内推码: 无%s\n\n");
            }
            if (draft.getRemarks() != null && !draft.getRemarks().isBlank()) {
                otherInfo.append(String.format("   • 备注: %s\n\n", draft.getRemarks()));
            }

            sb.append("\n📝 **其他信息**:\n\n");
            sb.append(otherInfo);
            sb.append("\n");

            // 分隔线
            sb.append("---\n\n");
        }

        sb.append("💡 操作提示:\n\n");
        sb.append("• 使用 `/updateDraft <ID>` 修改某条草稿信息\n\n");
        sb.append("• 使用 `/publishDrafts <ID1,ID2,...>` 发布选中的草稿\n\n");
        sb.append("• 例如: `/publishDrafts 2,3` 发布id=2,3的岗位");

        return sb.toString();
    }

    @Tool(description = """
            更新指定ID的职位草稿信息,修正错误或补充缺失字段。
                        
            **触发时机**: 当用户表达以下意图时调用此工具:
            - 修改信息: "修改第X条"、"更正公司信息"、"更新薪资"
            - 补充字段: "补充学历要求"、"添加工作地点"、"完善信息"
            - 纠正错误: "公司名错了"、"岗位名称不对"、"地点有误"
            - 调整内容: "改一下这个"、"调整为..."、"换成..."
            - 命令触发: 用户使用 `/updateDraft <ID>` 命令
                        
            **使用场景**:
            1. 审查草稿时发现信息有误需要修正
            2. 大模型提取的信息不完整需要补充
            3. 用户主动要求修改某条草稿的特定字段
            4. 用户通过命令直接触发更新操作
                        
            **参数说明**:
            - draftId: 要修改的草稿ID(数字)
            - jobInfo: 包含要更新字段的JobInfo对象
              * 只需传入需要修改的字段,其他字段保持null即可
              * 支持修改: companyName, position, salary, education, experience等所有字段
              * 示例: {"companyName": "新公司名", "salary": "20k-30k"}
                        
            **注意事项**:
            - 只有传入的字段会被更新,未传入的字段保持不变
            - 更新后可以再次调用此工具继续修改其他字段
            - 确认无误后应调用 publishDraftsToOfficial 发布
            - 如果draftId不存在,会返回错误提示
            - 如果是通过 `/updateDraft <ID>` 命令触发,需要先询问用户要修改哪些字段
                        
            **典型对话**:
            用户: "修改第1条,公司名改为XXX"
            → 调用此工具,传入 draftId=1, jobInfo={"companyName": "XXX"}
                        
            用户: "第2条的薪资改成20k-30k,学历改成本科"
            → 调用此工具,传入 draftId=2, jobInfo={"salary": "20k-30k", "education": "本科"}
                        
            用户: "/updateDraft 3"
            → 先询问用户要修改哪些字段,然后根据回复调用此工具
            """, returnDirect = true)
    public String updateDraftJobInfo(
            @JsonPropertyDescription("草稿ID")
            long draftId,
            @JsonPropertyDescription("要更新的职位信息,只需传入需要修改的字段")
            FetchedJobInfo jobInfo,
            ToolContext toolContext) {
        log.info("工具调用：更新草稿, draftId={}, jobInfo={}", draftId, jobInfo);

        try {
            boolean success = jobInfoPersistService.updateDraft(draftId, jobInfo);

            if (success) {
                return String.format(
                        "✅ 草稿更新成功\n\n" +
                                "📋 草稿ID: %d\n" +
                                "💡 提示:\n" +
                                "• 可以继续修改其他字段\n" +
                                "• 确认无误后使用 `/publishDrafts` 发布",
                        draftId
                );
            } else {
                return String.format("❌ 更新失败\n\n📋 草稿ID: %d\n💡 原因: 草稿不存在或已被删除", draftId);
            }
        } catch (Exception e) {
            log.error("更新草稿失败: draftId={}", draftId, e);
            return String.format(
                    "❌ 更新失败\n\n" +
                            "📋 草稿ID: %d\n" +
                            "💡 错误: %s\n" +
                            "请检查JSON格式是否正确",
                    draftId, e.getMessage()
            );
        }
    }

    @Tool(description = """
            将审核通过的草稿职位发布到正式数据库。
                        
            **触发时机**: 当用户表达以下意图时调用此工具:
            - 确认发布: "发布"、"确认"、"同意"、"可以发布"、"没问题"
            - 上架操作: "上架"、"上线"、"公开"、"对外发布"
            - 审批通过: "审核通过"、"批准"、"通过"、"OK"
            - 批量操作: "全部发布"、"发布这些"、"发布选中的"
                        
            **使用场景**:
            1. 用户查看待审核草稿后表示确认
            2. 用户说"发布第1,2,3条"或"发布所有"
            3. 用户回复"确认"、"可以"、"好的"等肯定词
                        
            **参数说明**:
            - draftIdsStr: 要发布的草稿ID列表，用逗号分隔
            - 如果用户说"全部"或"所有"，传入之前查询到的所有ID
            - 如果用户指定具体ID，如"发布1,2,3"，传入"1,2,3"
                        
            **注意事项**:
            - 发布后职位将对所有用户可见
            - 发布操作不可逆，请确保用户已确认
            - 如果用户未指定ID，应先询问要发布哪些草稿
            """, returnDirect = true)
    public String publishDraftsToOfficial(
            @JsonPropertyDescription("要发布的草稿ID列表，用逗号分隔，如: 1,2,3")
            String draftIdsStr,
            ToolContext toolContext) {
        log.info("工具调用：发布草稿, draftIds={}", draftIdsStr);

        try {
            // 解析ID列表
            String[] idStrings = draftIdsStr.split(",");
            List<Long> draftIds = new ArrayList<>();
            for (String idStr : idStrings) {
                try {
                    draftIds.add(Long.parseLong(idStr.trim()));
                } catch (NumberFormatException e) {
                    return String.format("❌ 无效的草稿ID: %s\n\n请使用数字ID，多个ID用逗号分隔", idStr.trim());
                }
            }

            if (draftIds.isEmpty()) {
                return "❌ 未提供有效的草稿ID";
            }

            // 发布草稿
            int publishedCount = jobInfoPersistService.publishDrafts(draftIds);

            return String.format(
                    "✅ 发布成功\n\n" +
                            "📊 发布结果:\n" +
                            "  • 请求发布: %d 条\n" +
                            "  • 成功发布: %d 条\n\n" +
                            "💡 提示:\n" +
                            "• 职位已发布到正式库\n" +
                            "• 所有用户现在可以查看这些职位\n" +
                            "• 可以在管理后台进一步管理",
                    draftIds.size(),
                    publishedCount
            );
        } catch (Exception e) {
            log.error("发布草稿失败: draftIds={}", draftIdsStr, e);
            return String.format(
                    "❌ 发布失败\n\n" +
                            "💡 错误: %s\n" +
                            "请稍后重试或联系管理员",
                    e.getMessage()
            );
        }
    }


    /**
     * 构建任务创建的友好提示信息
     *
     * @param taskResponse 任务响应
     * @param sourceType   来源类型(网页/文本/文件/图片)
     * @return 友好的提示消息
     */
    public String buildTaskCreatedMessage(JobFetchTaskResponse taskResponse, String sourceType) {
        return "\n\n✅ 已创建" + sourceType + "职位提取任务\n\n" +
                "📋 任务ID: `" + taskResponse.getTaskId() + "`\n\n" +
                "📊 当前状态: " + getStatusEmoji(taskResponse.getStatus()) + " " + getStatusText(taskResponse.getStatus()) + "\n\n" +
                "💡 提示:\n\n" +
                "• 任务正在后台执行中,请稍候...\n\n" +
                "• 您可以通过任务ID查询进度\n\n" +
                "• 提取完成后会主动通知您\n\n" +
                "🔍 查询命令: `/task " + taskResponse.getTaskId() + "`\n\n";
    }

    /**
     * 获取状态对应的Emoji
     */
    private String getStatusEmoji(String status) {
        return switch (status) {
            case "PENDING" -> "⏳";
            case "RUNNING" -> "🔄";
            case "SUCCESS" -> "✅";
            case "FAILED" -> "❌";
            default -> "❓";
        };
    }

    /**
     * 获取状态的中文描述
     */
    private String getStatusText(String status) {
        return switch (status) {
            case "PENDING" -> "等待执行";
            case "RUNNING" -> "执行中";
            case "SUCCESS" -> "已完成";
            case "FAILED" -> "失败";
            default -> "未知";
        };
    }
}
