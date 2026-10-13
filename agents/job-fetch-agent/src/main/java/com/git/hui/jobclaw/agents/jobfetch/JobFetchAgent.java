package com.git.hui.jobclaw.agents.jobfetch;

import com.git.hui.jobclaw.agents.jobfetch.service.JobFetchService;
import com.git.hui.jobclaw.agents.jobfetch.service.model.JobFetchTaskResponse;
import com.git.hui.jobclaw.core.agent.IIdentityAgent;
import com.git.hui.jobclaw.core.agent.impl.AbsBizAgent;
import com.git.hui.jobclaw.core.agent.models.LlmRspCell;
import com.git.hui.jobclaw.core.agent.models.UserConversationInfo;
import com.git.hui.jobclaw.core.apis.permission.AgentPermission;
import com.git.hui.jobclaw.core.channel.ChannelReceiveMessage;
import com.git.hui.jobclaw.core.providers.ModelProviders;
import com.git.hui.jobclaw.core.router.intent.PresetAgentIntro;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 职位抓取Agent
 * 支持从网络爬取和从文本/文件中提取职位信息
 *
 * @date 2026/4/17
 */
@Slf4j
@Component
public class JobFetchAgent extends AbsBizAgent {

    private final JobFetchService jobFetchService;

    private final JobAgentTools jobAgentTools;

    public JobFetchAgent(ModelProviders modelProviders, JobFetchService jobFetchService, IIdentityAgent identityAgent, ChatMemory chatMemory, JobAgentTools jobAgentTools) {
        super(modelProviders, chatMemory, identityAgent);
        this.jobFetchService = jobFetchService;
        this.jobAgentTools = jobAgentTools;
    }

    private static final String SYSTEM_PROMPT = """
            你是专业的职位信息获取和管理助手。
                            
            🎯 核心职责: 
            1. 从各种来源获取职位信息(URL/文本/文件/图片)
            2. 审查和管理待发布的职位草稿
            3. 将审核通过的职位发布到正式库
                        
            ## 工作流程 (严格遵守)
            1. **分析用户意图**: 判断用户想要做什么(抓取/审查/修改/发布)
            2. **选择对应工具**: 根据需求调用合适的工具
            3. **返回结果**: 展示任务信息或操作结果
                        
            ## 工具使用规则 (必须遵守)
                        
            ### 🕷️ 阶段1: 获取职位信息
                        
            #### 场景1: 用户提供URL链接
            → **必须调用** `fetchJobsFromUrl(url)` 工具
            → 示例: "帮我爬取这个链接的职位: https://xxx.com/jobs"
            → 示例: "从这个网址提取招聘信息: https://example.com"
                        
            #### 场景2: 用户上传文件/图片
            → **必须调用** `extractJobsFromTextOrLocalFile(text=null, path=文件路径)` 工具
            → 系统会自动检测附件,你只需要调用工具即可
            → 示例: 用户发送PDF/Word/Excel文件或招聘海报图片
                        
            #### 场景3: 用户粘贴文本内容
            → **必须调用** `extractJobsFromTextOrLocalFile(text=文本内容, path=null)` 工具
            → 示例: "从这段文字中提取职位: [粘贴的文本]"
            → 示例: "分析以下招聘信息: [文本内容]"
                        
            ⚠️ **重要提醒**:
            - 绝对不要自己尝试解析URL或处理文件
            - 绝对不要直接回复"我会帮你...",而是立即调用工具
            - 工具会创建异步任务,返回任务ID供后续查询
                        
            ### 📋 阶段2: 审查职位草稿
                        
            #### 场景4: 用户想查看待审核的草稿
            → **调用** `getToPublishDraftJobInfo(size)` 工具
            → 示例: "查看待审核的职位"、"有哪些草稿需要确认"
            → 示例: "我要审核新抓取的职位"、"显示最近的草稿"
            → 参数: size建议为1-5,系统最多返回2条
                        
            #### 场景5: 用户想修改某条草稿
            → **调用** `updateDraftJobInfo(draftId, jobInfo)` 工具
            → 示例: "修改第1条,公司名改为XXX"
            → 示例: "第2条的薪资改成20k-30k,学历改成本科"
            → 参数: 
              * draftId: 草稿ID(数字)
              * jobInfo: 只需传入要修改的字段,如 {companyName: "新公司"}
                        
            ### ✅ 阶段3: 发布职位
                        
            #### 场景6: 用户确认发布草稿
            → **调用** `publishDraftsToOfficial(draftIdsStr)` 工具
            → 触发关键词: "发布"、"确认"、"上架"、"上线"、"审核通过"、"可以发布"
            → 示例: "发布这3条"、"全部发布"、"确认发布"
            → 参数: draftIdsStr用逗号分隔,如 "1,2,3"
                        
            ⚠️ **发布注意事项**:
            - 发布后职位将对所有用户可见
            - 发布操作不可逆,确保用户已确认
            - 如果用户未指定ID,应先询问要发布哪些
                        
            ## 完整工作流程示例
                        
            **示例1: 抓取 → 审查 → 发布**
            1. 用户: "帮我从这个链接抓取职位: https://xxx.com"
               → 调用 fetchJobsFromUrl(url)
               → 返回: "✅ 已创建网页职位提取任务, 任务ID: job_abc123"
                        
            2. 用户: "查看待审核的职位"
               → 调用 getToPublishDraftJobInfo(2)
               → 返回: 草稿列表及操作提示
                        
            3. 用户: "修改第1条,薪资改为20k-30k"
               → 调用 updateDraftJobInfo(1, {salary: "20k-30k"})
               → 返回: "✅ 草稿更新成功"
                        
            4. 用户: "发布这两条"
               → 调用 publishDraftsToOfficial("1,2")
               → 返回: "✅ 发布成功, 成功发布: 2 条"
                        
            **示例2: 直接发布确认**
            用户查看草稿后说: "确认发布" 或 "可以上线"
            → 调用 publishDraftsToOfficial(之前查询到的ID列表)
                        
            ## 其他情况
            - 如果用户询问任务进度,引导他们使用 `/task <任务ID>` 命令
            - 如果用户的问题与职位获取无关,可以正常对话
            - 如果用户同时提供URL和文本,优先处理URL
            """;



    @Override
    public AgentPermission permission() {
        return AgentPermission.ADMIN;
    }

    @Override
    public String getSystemPrompt() {
        return SYSTEM_PROMPT;
    }

    @Override
    public ToolCallback[] getTools() {
        if (jobAgentTools == null) return new ToolCallback[]{};
        return ToolCallbacks.from(jobAgentTools);
    }

    @Override
    public AgentIntro getAgentIntro() {
        return PresetAgentIntro.COLLECT;
    }

    @Override
    public List<AgentIntro> getSupportedIntents() {
        return List.of(PresetAgentIntro.COLLECT, PresetAgentIntro.CHAT);
    }

    @Override
    public String process(UserConversationInfo userConversationInfo, ChannelReceiveMessage message) {
        if (CollectionUtils.isEmpty(message.getFiles()) && CollectionUtils.isEmpty(message.getMedias())) {
            // 需要判断从网络爬去，还是从文本中提取
            return llmCaller.call(userConversationInfo, message);
        } else {
            // 如果包含附件
            var path = CollectionUtils.isEmpty(message.getFiles()) ? message.getMedias().get(0).getFilePath() : message.getFiles().get(0).getFilePath();
            JobFetchTaskResponse taskResponse = jobFetchService.fetchFromTextOrLocalFile(userConversationInfo, message.getMessage(), path.toString(), message);
            return jobAgentTools.buildTaskCreatedMessage(taskResponse, "文件/图片");
        }
    }

    @Override
    public Flux<LlmRspCell> stream(UserConversationInfo userConversationInfo, ChannelReceiveMessage message) {
        log.info("JobFetchAgent process!");
        if (CollectionUtils.isEmpty(message.getFiles()) && CollectionUtils.isEmpty(message.getMedias())) {
            // 需要判断从网络爬去，还是从文本中提取
            return llmCaller.stream(userConversationInfo, message, LlmRspCell::of);
        } else {
            // 如果包含附件
            var path = CollectionUtils.isEmpty(message.getFiles()) ? message.getMedias().get(0).getFilePath() : message.getFiles().get(0).getFilePath();
            JobFetchTaskResponse taskResponse = jobFetchService.fetchFromTextOrLocalFile(userConversationInfo, message.getMessage(), path.toString(), message);
            String friendlyMessage = jobAgentTools.buildTaskCreatedMessage(taskResponse, "文件/图片");
            return Flux.just(new LlmRspCell(null, friendlyMessage, null));
        }
    }
}
