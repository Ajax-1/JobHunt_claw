package com.git.hui.jobclaw.core.agent.impl;

import com.git.hui.jobclaw.core.agent.IIdentityAgent;
import com.git.hui.jobclaw.core.agent.llm.UserPreferenceBasedLlmCaller;
import com.git.hui.jobclaw.core.agent.models.LlmRspCell;
import com.git.hui.jobclaw.core.agent.models.UserConversationInfo;
import com.git.hui.jobclaw.core.apis.permission.AgentPermission;
import com.git.hui.jobclaw.core.channel.ChannelReceiveMessage;
import com.git.hui.jobclaw.core.providers.ModelProviders;
import com.git.hui.jobclaw.core.router.intent.PresetAgentIntro;
import com.git.hui.jobclaw.core.tasks.TaskManager;
import com.git.hui.jobclaw.core.tools.AutoDiscoveredTool;
import jakarta.annotation.PostConstruct;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 通用的聊天Agent，主要用于用户未指定特定业务Agent时，与用户继续普通的聊天场景
 * @date 2026/4/17
 */
@Component
public class CustomChatBizAgent extends AbsBizAgent {
    private final TaskManager taskManager;
    private final Resource workspace;
    private final List<AutoDiscoveredTool<?>> autoDiscoveredTools;

    public CustomChatBizAgent(ModelProviders modelProviders,
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
        return PresetAgentIntro.CHAT;
    }

    @Override
    public List<AgentIntro> getSupportedIntents() {
        return List.of(PresetAgentIntro.CHAT);
    }

    @Override
    public String process(UserConversationInfo userConversationInfo, ChannelReceiveMessage message) {
        return llmCaller.call(userConversationInfo, message);
    }

    @Override
    public Flux<LlmRspCell> stream(UserConversationInfo userConversationInfo, ChannelReceiveMessage message) {
        return llmCaller.stream(userConversationInfo, message, LlmRspCell::of);
    }

    @Override
    public String getSystemPrompt() {
        return """
                You are an interactive assistant working for JobClaw helping them with all their tasks and todos. Use the skills and the tools available to you to assist the user.
                            
                ## 可用的系统命令
                用户可以通过以下命令与系统交互:
                - `/help` - 查看帮助信息
                - `/agents` - 查看所有可用的Agent列表
                - `/current` - 查看当前会话绑定的Agent
                - `/model` - 查看和切换默认模型
                - `/agent <agentId>` - 切换到指定的Agent
                            
                记住: 你的目标是成为用户值得信赖的伙伴,在他们需要时提供恰到好处的帮助!
                                
                请注意，优先使用提供的工具来实现用户的需求，使用使用中文进行返回
                """;
    }
}
