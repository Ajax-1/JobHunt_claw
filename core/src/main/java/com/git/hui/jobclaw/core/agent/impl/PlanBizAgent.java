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
import com.git.hui.jobclaw.core.tools.PlanNotebookCapability;
import jakarta.annotation.PostConstruct;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * Executes complex requests in plan mode with the auto-discovered PlanNotebook tools.
 * AIDEV-NOTE: Plan tools stay plugin-owned.
 */
@Component
public class PlanBizAgent extends AbsBizAgent {

    private final TaskManager taskManager;
    private final Resource workspace;
    private final List<AutoDiscoveredTool<?>> autoDiscoveredTools;

    public PlanBizAgent(ModelProviders modelProviders,
                        IIdentityAgent identityAgent,
                        ChatMemory chatMemory,
                        TaskManager taskManager,
                        @Value("${agent.workspace:Unknown}") Resource workspace,
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
        return PresetAgentIntro.PLAN;
    }

    @Override
    public boolean isAvailable() {
        return autoDiscoveredTools.stream()
                .map(AutoDiscoveredTool::tool)
                .anyMatch(PlanNotebookCapability.class::isInstance);
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
                你是 JobClaw 的计划模式助手，负责将复杂目标拆解为清晰、可执行、可追踪的计划，并使用可用工具推进计划。

                ## 计划模式工作流
                1. 理解用户目标、约束与完成标准。缺少关键条件时先询问用户。
                2. 对需要多个步骤的任务，必须先调用 `createPlan` 创建计划，再开始执行。
                3. 开始某个子任务前，调用 `updateSubtaskState` 将其标记为 `IN_PROGRESS`。
                4. 完成子任务后立即调用 `finishSubtask`，记录简洁的结果或证据。
                5. 无法继续的子任务调用 `abandonSubtask`，记录原因，不得伪装成已完成。
                6. 使用 `getCurrentPlan` 检查当前计划和进度，确保同一时间只有一个子任务处于 `IN_PROGRESS`。
                7. 全部子任务完成后，向用户汇总结果。仅在计划不再需要时调用 `clearPlan`。

                ## 执行原则
                - 计划是执行工具，不要只创建计划而不推进。
                - 子任务应当具体、可验证，并按依赖顺序排列。
                - 用户改变目标时，及时创建替代计划或调整状态。
                - 不要虚构工具执行结果、岗位信息或完成状态。
                - 优先使用系统提供的工具完成任务，使用中文回复。

                用户可使用 `/reset` 退出计划模式并解除当前 Agent 绑定。
                """;
    }
}
