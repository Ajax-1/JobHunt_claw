package com.git.hui.jobclaw.agents;

import com.git.hui.jobclaw.core.agent.IIdentityAgent;
import com.git.hui.jobclaw.core.agent.impl.AbsBizAgent;
import com.git.hui.jobclaw.core.agent.llm.UserPreferenceBasedLlmCaller;
import com.git.hui.jobclaw.core.agent.models.LlmRspCell;
import com.git.hui.jobclaw.core.agent.models.UserConversationInfo;
import com.git.hui.jobclaw.core.apis.permission.AgentPermission;
import com.git.hui.jobclaw.core.channel.ChannelReceiveMessage;
import com.git.hui.jobclaw.core.providers.ModelProviders;
import com.git.hui.jobclaw.core.router.intent.PresetAgentIntro;
import com.git.hui.jobclaw.core.tasks.TaskManager;
import com.git.hui.jobclaw.core.tools.AutoDiscoveredTool;
import com.git.hui.jobclaw.plugins.jobs.JobLibraryTool;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 职位推荐Agent
 * 根据用户偏好智能推荐匹配的职位信息
 *
 * @date 2026/4/21
 */
@Slf4j
@Component
public class JobRecommendAgent extends AbsBizAgent {
    private final JobLibraryTool jobLibraryTool;
    private final TaskManager taskManager;
    private final Resource workspace;
    private final List<AutoDiscoveredTool<?>> autoDiscoveredTools;

    public JobRecommendAgent(ModelProviders modelProviders,
                             IIdentityAgent identityAgent,
                             ChatMemory chatMemory,
                             TaskManager taskManager,
                             @Value("${agent.workspace:Unknown}")
                             Resource workspace,
                             List<AutoDiscoveredTool<?>> autoDiscoveredTools,
                             JobLibraryTool jobLibraryTool) {
        super(modelProviders, chatMemory, identityAgent);
        this.jobLibraryTool = jobLibraryTool;
        this.taskManager = taskManager;
        this.autoDiscoveredTools = autoDiscoveredTools;
        this.workspace = workspace;
    }

    @PostConstruct
    public void init() {
        this.llmCaller = new UserPreferenceBasedLlmCaller(
                modelProviders, identityAgent, chatMemory, taskManager, autoDiscoveredTools, getSystemPrompt()
        );
        this.llmCaller.setWorkspace(workspace);
    }


    private static final String SYSTEM_PROMPT = """
            你是专业的职位推荐助手,帮助用户找到最适合的职位。
                        
            🎯 核心职责:
            1. 了解用户的求职偏好和需求
            2. 根据偏好从职位库中智能检索匹配的职位
            3. 提供个性化的职位推荐和建议
                        
            ## 工作流程 (严格遵守)
            1. **了解用户需求**: 通过对话了解用户的求职偏好
            2. **查询可用选项**: 如果用户不确定,先调用 getAvailableFilterOptions() 查看可选条件
            3. **构建偏好对象**: 根据用户描述构建 UserPreference 对象
            4. **执行检索**: 调用 searchJobsByPreference() 获取匹配职位
            5. **展示结果**: 将检索结果友好地呈现给用户
                        
            ## 工具使用规则 (必须遵守)
                        
            ### 工具1: getAvailableFilterOptions()
            **用途**: 查询可用的筛选选项
            **触发时机**:
            - 用户询问有哪些筛选项
            - 用户不确定应该输入什么值
            - 用户在构建偏好前需要了解选项
                        
            **示例**:
            用户: "有哪些公司类型?"
            → 调用 getAvailableFilterOptions()
                        
            ### 工具2: searchJobsByPreference(preference)
            **用途**: 根据用户偏好检索职位
            **触发时机**:
            - 用户描述了求职偏好
            - 用户明确要求找某类职位
            - 用户想要个性化推荐
                        
            **参数说明**:
            - preference: UserPreference 对象
              * companyTypes: 公司类型列表(可选)
              * industries: 行业列表(可选)
              * locations: 地点列表(可选)
              * recruitmentTarget: 招聘对象(可选)
              * recruitmentTypes: 招聘类型列表(可选)
              * positions: 岗位关键词列表(可选)
              * pageSize: 返回数量,默认10
                        
            **示例**:
            用户: "帮我找北京的国企秋招职位"
            → 调用 searchJobsByPreference({locations:["北京"], companyTypes:["国企"], recruitmentTypes:["秋招"]})
                        
            ## 交互策略
                        
            ### 场景1: 用户描述模糊
            用户: "我想找工作"
            → 引导用户提供更多信息:
              "请问您期望的工作地点、行业、岗位类型是什么?"
              "您是应届毕业生还是有工作经验?"
                        
            ### 场景2: 用户描述清晰
            用户: "我是2026届毕业生,想找北京的IT公司研发岗位"
            → 直接构建偏好并检索:
              searchJobsByPreference({
                recruitmentTarget: "2026年毕业生",
                locations: ["北京"],
                industries: ["IT/互联网"],
                positions: ["研发","工程师"]
              })
                        
            ### 场景3: 用户想了解选项
            用户: "有哪些行业可以选择?"
            → 调用 getAvailableFilterOptions()
            → 展示所有筛选项后,引导用户选择
                        
            ### 场景4: 无匹配结果
            → 展示用户的筛选条件
            → 提供放宽条件的建议
            → 鼓励用户调整搜索策略
                        
            ## 重要提醒
            ⚠️ **必须使用工具**: 不要自己编造职位信息,必须通过工具从数据库检索
            ⚠️ **友好引导**: 如果用户描述不清,要主动引导而非直接拒绝
            ⚠️ **个性化服务**: 根据用户的历史对话记住其偏好
            ⚠️ **准确映射**: 将用户的自然语言描述准确映射到字典值
                        
            ## 对话风格
            - 专业但亲切
            - 主动提供帮助
            - 简洁明了地展示结果
            - 适时给出建议和指导
            """;



    @Override
    public AgentPermission permission() {
        return AgentPermission.TOTAL;
    }

    @Override
    public AgentIntro getAgentIntro() {
        return PresetAgentIntro.RECOMMEND;
    }


    @Override
    public ToolCallback[] getTools() {
        return ToolCallbacks.from(jobLibraryTool);
    }

    @Override
    public List<AgentIntro> getSupportedIntents() {
        return List.of(PresetAgentIntro.RECOMMEND, PresetAgentIntro.CHAT);
    }

    @Override
    public String getSystemPrompt() {
        return SYSTEM_PROMPT;
    }

    @Override
    public String process(UserConversationInfo userConversationInfo, ChannelReceiveMessage message) {
        log.info("JobRecommendAgent process: {}", message.getMessage());
//
//        Prompt prompt = SpringUtil.getBean(UserPreferenceBasedLlmCaller.class)
//                .buildSoulPrompt(userConversationInfo.jobClawUserId(), getSystemPrompt(), message);

        return llmCaller.call(userConversationInfo, message);
    }

    @Override
    public Flux<LlmRspCell> stream(UserConversationInfo userConversationInfo, ChannelReceiveMessage message) {
        log.info("JobRecommendAgent stream: {}", message.getMessage());
//        Prompt prompt = SpringUtil.getBean(UserPreferenceBasedLlmCaller.class)
//                .buildSoulPrompt(userConversationInfo.jobClawUserId(), getSystemPrompt(), message);
        return llmCaller.stream(userConversationInfo, message, LlmRspCell::of);
    }
}
