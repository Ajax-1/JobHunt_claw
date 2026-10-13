package com.git.hui.jobclaw.core.agent.impl;

import com.git.hui.jobclaw.core.agent.IIdentityAgent;
import com.git.hui.jobclaw.core.agent.models.LlmRspCell;
import com.git.hui.jobclaw.core.agent.models.UserConversationInfo;
import com.git.hui.jobclaw.core.apis.permission.AgentPermission;
import com.git.hui.jobclaw.core.channel.ChannelReceiveMessage;
import com.git.hui.jobclaw.core.providers.ModelProviders;
import com.git.hui.jobclaw.core.router.intent.PresetAgentIntro;
import com.git.hui.jobclaw.core.tasks.TaskManager;
import com.git.hui.jobclaw.core.tools.TaskTool;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/**
 * 任务提醒类的业务Agent
 * @date 2026/4/17
 */
@Component
public class TaskBizAgent extends AbsBizAgent {
    private final TaskManager taskManager;

    public TaskBizAgent(ModelProviders modelProviders,
                        IIdentityAgent identityAgent,
                        ChatMemory chatMemory,
                        TaskManager taskManager) {
        super(modelProviders, chatMemory, identityAgent);
        this.taskManager = taskManager;
    }

    @Override
    public AgentPermission permission() {
        return AgentPermission.TOTAL;
    }

    @Override
    public AgentIntro getAgentIntro() {
        return PresetAgentIntro.TASK;
    }

    @Override
    public String process(UserConversationInfo userConversationInfo, ChannelReceiveMessage message) {
        return llmCaller.call(userConversationInfo, new Prompt(message.getMessage()));
    }

    @Override
    public Flux<LlmRspCell> stream(UserConversationInfo userConversationInfo, ChannelReceiveMessage message) {
        return llmCaller.stream(userConversationInfo, message, LlmRspCell::of);
    }

    @Override
    public ToolCallback[] getTools() {
        return ToolCallbacks.from(TaskTool.builder().taskManager(taskManager).build());
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
