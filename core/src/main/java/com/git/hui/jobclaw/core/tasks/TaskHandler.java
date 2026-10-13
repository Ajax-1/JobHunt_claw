package com.git.hui.jobclaw.core.tasks;

import com.git.hui.jobclaw.core.agent.llm.LlmCaller;
import com.git.hui.jobclaw.core.agent.models.UserConversationInfo;
import com.git.hui.jobclaw.core.bus.ChannelEventPublisher;
import com.git.hui.jobclaw.core.preference.AiUserPreferenceProperties;
import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.jobs.context.JobRunrDashboardLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;

@Component
public class TaskHandler {

    private static final Logger LOGGER = new JobRunrDashboardLogger(LoggerFactory.getLogger(TaskHandler.class));

    private final LlmCaller llmCaller;
    private final TaskRepository taskRepository;

    private final AiUserPreferenceProperties aiUserPreferenceProperties;

    private final ChannelEventPublisher channelEventPublisher;

    public TaskHandler(LlmCaller simpleLlmCaller, TaskRepository taskRepository, AiUserPreferenceProperties aiModelProperties, ChannelEventPublisher channelEventPublisher) {
        this.llmCaller = simpleLlmCaller;
        this.taskRepository = taskRepository;
        this.aiUserPreferenceProperties = aiModelProperties;
        this.channelEventPublisher = channelEventPublisher;
    }

    @Job(name = "%0", retries = 3)
    public void executeTask(String taskId) {
        Task task = taskRepository.getTaskById(taskId);

        if (!Task.Status.todo.equals(task.getStatus())) {
            throw new IllegalStateException(
                    "Cannot handle task '" + task.getName() + "' with status " + task.getStatus() + ". Only tasks that have status todo can be run");
        }

        Task inProgress = taskRepository.save(task.withStatus(Task.Status.in_progress));
        try {
            LOGGER.info("Starting task: {}", task);
            String agentInput = formatTaskForAgent(inProgress);
            var result = llmCaller.call(new UserConversationInfo(task.getJobClawUserId(), null, task.getId(), false),
                    new Prompt(agentInput), TaskResult.class);
            taskRepository.save(inProgress.withFeedback(result.feedback()).withStatus(result.newStatus()));
            notifyUser(task, result);
            LOGGER.info("Finished task: {} with status {}", task.getName(), result.newStatus());
        } catch (Exception e) {
            taskRepository.save(inProgress.withStatus(Task.Status.todo));
            throw e;
        }
    }

    private void notifyUser(Task task, TaskResult result) {
        try {
            String userId = task.getJobClawUserId();
            var pre = aiUserPreferenceProperties.getUserPreference(userId);
            if (pre != null) {
                for (var channel : pre.getChannels()) {
                    // fixme 主动给用户发送消息，但是这里没有考虑通道具体的回调状态，如果通道发送失败了，是否需要重试、或者路由到下一个通道? 这些重试&自动路由的逻辑，后续可以统一在消息消费端进行处理
                    var tag = channelEventPublisher.publishProactiveMessage("TRSP_" + task.getId(),
                            userId,
                            channel,
                            result.feedback());
                    if (tag) {
                        LOGGER.info("Notified user about task '{}'", task.getName());
                        return;
                    }
                }
                LOGGER.warn("Failed to notify user about task '{}': no suitable channel found", task.getName());
            } else {
                LOGGER.warn("Failed to notify user about task '{}': no preference found for user '{}'", task.getName(), userId);
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to notify user about task '{}': {}", task.getName(), e.getMessage());
        }
    }

    private String formatTaskForAgent(Task task) {
        return String.format("""
                你是一个智能任务助手，需要执行以下任务并返回结果。
                                
                ## 任务信息
                - 任务名称: %s
                - 任务描述: %s
                                
                ## 执行要求
                1. **语言要求**: 必须使用**中文**回复用户，语气友好、自然
                2. **内容要求**: 
                   - 如果是提醒类任务，直接告诉用户提醒的内容和时间
                   - 如果是查询类任务，返回查询结果摘要
                   - 如果是操作类任务，说明已完成的动作
                3. **状态选择**: 
                   - 任务成功完成 → 返回 'completed'
                   - 需要用户进一步确认或提供信息 → 返回 'awaiting_human_input'
                4. **反馈格式**: 
                   - 简洁明了，避免技术术语
                   - 例如："⏰ 提醒：您有会议将在2分钟后开始，请做好准备"
                   - 例如："✅ 已完成：已为您收集了今天的校招信息，共找到5个匹配岗位"
                                
                ## 返回格式
                请以 JSON 格式返回，包含两个字段：
                - newStatus: 任务状态（'completed' 或 'awaiting_human_input'）
                - feedback: 给用户的反馈消息（中文，友好自然）
                                
                示例：
                {
                  "newStatus": "completed",
                  "feedback": "⏰ 提醒：您的会议将在2分钟后开始，请提前准备好相关资料"
                }
                """, task.getName(), task.getDescription());
    }

    public record TaskResult(Task.Status newStatus, String feedback) {
    }
}
