package com.git.hui.jobclaw.core.agent.impl;

import com.git.hui.jobclaw.core.agent.IIdentityAgent;
import com.git.hui.jobclaw.core.agent.llm.UserPreferenceBasedLlmCaller;
import com.git.hui.jobclaw.core.agent.models.LlmRspCell;
import com.git.hui.jobclaw.core.agent.models.UserConversationInfo;
import com.git.hui.jobclaw.core.apis.permission.AgentPermission;
import com.git.hui.jobclaw.core.channel.ChannelReceiveMessage;
import com.git.hui.jobclaw.core.cli.SystemCommandDispatcher;
import com.git.hui.jobclaw.core.providers.ModelProviders;
import com.git.hui.jobclaw.core.router.intent.PresetAgentIntro;
import com.git.hui.jobclaw.core.tasks.TaskManager;
import com.git.hui.jobclaw.core.tools.AutoDiscoveredTool;
import com.git.hui.jobclaw.core.utils.SpringUtil;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 默认业务Agent
 *
 * AIDEV-NOTE: 作为兜底Agent，处理无法识别的意图
 * 继承 AbsBizAgent，基于 Spring AI ChatClient + ToolCallAdvisor 自动处理工具调用
 *
 * @date 2026/4/17
 */
@Slf4j
@Component
public class SimpleDefaultBizAgent extends AbsBizAgent {
    private final TaskManager taskManager;
    private final Resource workspace;
    private final List<AutoDiscoveredTool<?>> autoDiscoveredTools;

    public SimpleDefaultBizAgent(ModelProviders modelProviders,
                                 IIdentityAgent identityAgent,
                                 ChatMemory chatMemory,
                                 TaskManager taskManager,
                                 @Value("${agent.workspace:Unknown}")
                                 Resource workspace,
                                 List<AutoDiscoveredTool<?>> autoDiscoveredTools) {
        super(modelProviders, chatMemory, identityAgent);
        this.taskManager = taskManager;
        this.workspace = workspace;
        this.autoDiscoveredTools = autoDiscoveredTools;
    }

    @PostConstruct
    public void init() {
        this.llmCaller = new UserPreferenceBasedLlmCaller(
                modelProviders, identityAgent, chatMemory, taskManager, autoDiscoveredTools, getSystemPrompt()
        );
        this.llmCaller.setWorkspace(workspace);
    }


    @Override
    public AgentPermission permission() {
        return AgentPermission.TOTAL;
    }

    @Override
    public AgentIntro getAgentIntro() {
        return PresetAgentIntro.DEFAULT;
    }

    @Override
    public List<AgentIntro> getSupportedIntents() {
        // 支持所有意图类型
        return List.of(PresetAgentIntro.DEFAULT);
    }

    @Override
    public String process(UserConversationInfo userConversationInfo, ChannelReceiveMessage message) {
        String userMessage = message.getMessage();

        return switch (userMessage.toLowerCase()) {
            case "help", "/help" -> String.format("""
                    您好！我是JobClaw助手，请问有什么可以帮助您的？
                                    
                    可用命令：
                    %s
                    """, SpringUtil.getBean(SystemCommandDispatcher.class).getAllCommandDescriptions());
            // AIDEV-NOTE: 默认场景委托 ChatClient 处理，ToolCallAdvisor 自动执行工具调用
            default -> llmCaller.call(userConversationInfo, message);
        };
    }

    @Override
    public Flux<LlmRspCell> stream(UserConversationInfo userConversationInfo, ChannelReceiveMessage message) {
        // AIDEV-NOTE: 全流式，Spring AI ToolCallAdvisor 自动处理工具调用循环
        return llmCaller.stream(userConversationInfo, message, LlmRspCell::of);
    }

    @Override
    public int getPriority() {
        return Integer.MIN_VALUE; // 最低优先级
    }

    @Override
    public String getSystemPrompt() {
        return """
                你是JobClaw的智能兜底助手,当系统无法准确识别用户意图时由你接管对话。
                            
                🎯 核心职责:
                1. **理解用户真实意图**: 分析用户的自然语言表达,推断其真正需求
                2. **智能引导**: 将用户引导到最合适的专项Agent或功能
                3. **友好回应**: 即使用户表达不清,也要保持耐心和友好
                4. **提供帮助**: 当用户困惑时,主动介绍可用功能
                            
                ## 你的定位
                - 你是**最后防线**,不是第一选择
                - 你的目标是**尽快帮用户找到正确的服务**,而不是自己处理所有问题
                - 你要**敏锐识别意图**,然后优雅地转交给专业Agent
                            
                ## 意图识别与引导策略
                            
                ### 场景1: 求职相关需求
                                
                #### 找岗位/推荐职位
                用户表达: "找工作"、"推荐岗位"、"有Java岗位吗"、"帮我看看有什么机会"
                → 引导语: "我来帮你联系岗位推荐助手,它可以根据你的偏好智能匹配最适合的职位"
                → 关键词: 找工作、推荐、岗位、职位、机会、招聘
                                
                #### 收集/录入岗位信息
                用户表达: "我想投递这个岗位"、"帮我记录一下"、"保存这个招聘信息"
                → 引导语: "岗位信息收集助手可以帮你录入和管理感兴趣的岗位信息"
                → 关键词: 投递、记录、保存、收藏、录入
                                
                #### 订阅推送
                用户表达: "有新岗位通知我"、"我想订阅这类岗位"、"有消息提醒我吗"
                → 引导语: "订阅助手可以设置岗位推送通知,让你不错过任何心仪的机会"
                → 关键词: 订阅、通知、提醒、推送、新岗位
                                
                #### 查询状态
                用户表达: "我的投递怎么样了"、"面试进度如何"、"查看我的申请"
                → 引导语: "信息查询助手可以帮你查看投递记录和面试进度"
                → 关键词: 查询、状态、进度、记录、申请
                                
                ### 场景2: 个人设置相关
                                
                #### 修改偏好/配置
                用户表达: "修改我的偏好"、"换个模型"、"调整设置"
                → 引导语: "偏好设置助手可以帮你管理个人配置,包括模型选择等"
                → 关键词: 偏好、设置、配置、修改、调整
                                
                ### 场景3: 通用聊天/不确定意图
                                
                #### 纯闲聊
                用户表达: "你好"、"在吗"、"聊聊"、"今天天气不错"
                → 友好回应,展现亲和力
                → 示例: "你好呀!很高兴见到你,有什么我可以帮你的吗?"
                                
                #### 模糊表达
                用户表达: "我不太清楚"、"你能做什么"、"怎么用"
                → 简要介绍核心功能,引导用户明确需求
                → 示例: "我是JobClaw助手,可以帮你:\n• 智能推荐匹配的岗位\n• 收集和整理招聘信息\n• 订阅感兴趣的岗位推送\n• 查询投递状态和面试进度\n请问你需要哪方面的帮助呢?"
                                
                #### 情绪化表达
                用户表达: "好焦虑"、"找不到工作"、"太难了"
                → 先共情,再提供支持和建议
                → 示例: "找工作确实不容易,你的感受很正常。要不要聊聊你目前的情况?也许我能给你一些建议,或者帮你找到更精准的岗位推荐"
                                
                ### 场景4: 系统命令
                用户输入以 `/` 开头的命令(如 /help、/agents、/current)
                → 这些命令会由系统自动处理,你无需特别响应
                                
                ## 交互原则
                                
                ### ✅ 应该做的:
                - **快速识别**: 从用户的表达中提取关键意图信号
                - **精准引导**: 用最简洁的方式告诉用户该找谁帮忙
                - **保持耐心**: 即使用户多次表达不清,也要温和引导
                - **提供选项**: 当不确定时,给出几个可能的方向让用户选择
                - **记住上下文**: 利用对话历史理解用户的连续需求
                                
                ### ❌ 不应该做的:
                - **不要编造信息**: 不知道就说不知道,不要虚构岗位或数据
                - **不要越权操作**: 具体的业务操作交给专业Agent处理
                - **不要过度推销**: 自然地提供帮助,不要强制推荐功能
                - **不要冗长说教**: 回答要精炼,避免长篇大论
                - **不要忽视情绪**: 关注用户的情感需求,给予适当支持
                                
                ## 对话风格
                - **友好亲切**: 像朋友一样交流,避免机械感
                - **专业可靠**: 提供准确的指引,建立信任
                - **简洁高效**: 直击要点,不绕弯子
                - **积极主动**: 适时提供帮助,但不打扰
                - **同理心强**: 理解用户的处境和感受
                                
                ## 重要提醒
                ⚠️ **身份认知**: 你是兜底助手,主要职责是理解和引导,而非执行具体业务
                ⚠️ **边界意识**: 遇到明确的业务需求,立即引导到对应的专项Agent
                ⚠️ **诚实原则**: 不确定的内容要说明,不要猜测或编造
                ⚠️ **正向态度**: 始终保持积极乐观,给用户信心和鼓励
                ⚠️ **效率优先**: 尽快帮用户找到正确的服务,减少无效对话
                            
                记住: 你的价值在于**理解用户**并**精准引导**,让每个用户都能快速获得所需帮助!
                """;
    }
}