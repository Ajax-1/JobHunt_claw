package com.git.hui.jobclaw.agents.jobfetch.cli;

import com.git.hui.jobclaw.agents.jobfetch.service.JobFetchService;
import com.git.hui.jobclaw.agents.jobfetch.service.model.JobFetchTaskResponse;
import com.git.hui.jobclaw.core.agent.models.UserConversationInfo;
import com.git.hui.jobclaw.core.channel.ChannelReceiveMessage;
import com.git.hui.jobclaw.core.cli.SystemCommandHandler;
import com.git.hui.jobclaw.core.router.intent.PresetAgentIntro;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Function;

/**
 * 任务查询命令处理器 (/task)
 *
 * AIDEV-NOTE: 用于查询职位抓取任务的执行状态和结果
 *
 * @date 2026/4/20
 */
@Slf4j
@Component
public class TaskQueryCommandHandler implements SystemCommandHandler {

    private final JobFetchService jobFetchService;
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public TaskQueryCommandHandler(JobFetchService jobFetchService) {
        this.jobFetchService = jobFetchService;
    }

    @Override
    public boolean handle(ChannelReceiveMessage msg,
                          UserConversationInfo conversationInfo,
                          String command,
                          Function<String, Boolean> process) {
        // 解析命令参数: /task [list|<taskId>]
        String[] parts = command.split("\\s+", 2);

        // 情况1: /task (无参数)
        if (parts.length == 1) {
            return process.apply(buildHelpMessage());
        }

        String param = parts[1].trim();

        // 情况2: /task list (查询任务列表)
        if ("list".equalsIgnoreCase(param)) {
            return handleListTasks(conversationInfo, process);
        }

        // 情况3: /task <taskId> (查询单个任务)
        return handleQueryTask(conversationInfo, param, process);
    }

    /**
     * 处理任务列表查询
     */
    private boolean handleListTasks(UserConversationInfo conversationInfo,
                                    Function<String, Boolean> process) {
        try {
            List<JobFetchTaskResponse> tasks = jobFetchService.listTasks(conversationInfo.jobClawUserId());
            return process.apply(buildTaskListMessage(tasks));
        } catch (Exception e) {
            log.error("查询任务列表失败: userId={}", conversationInfo.jobClawUserId(), e);
            return process.apply("❌ 查询任务列表失败，请稍后重试");
        }
    }

    /**
     * 处理单个任务查询
     */
    private boolean handleQueryTask(UserConversationInfo conversationInfo,
                                    String taskId,
                                    Function<String, Boolean> process) {
        log.info("查询任务状态: userId={}, taskId={}", conversationInfo.jobClawUserId(), taskId);

        try {
            JobFetchTaskResponse task = jobFetchService.queryTask(conversationInfo.jobClawUserId(), taskId);
            return process.apply(buildTaskStatusMessage(task));
        } catch (IllegalArgumentException e) {
            return process.apply("❌ 任务不存在或无权限访问\n\n请检查任务ID是否正确，或使用 `/help` 查看帮助。");
        } catch (Exception e) {
            log.error("查询任务失败: taskId={}", taskId, e);
            return process.apply("❌ 查询任务失败，请稍后重试");
        }
    }

    @Override
    public PresetAgentIntro getIntentType() {
        return PresetAgentIntro.TASK_QUERY;
    }

    @Override
    public String getDescription() {
        return "查询职位抓取任务状态";
    }

    /**
     * 构建任务列表消息
     */
    private String buildTaskListMessage(List<JobFetchTaskResponse> tasks) {
        StringBuilder sb = new StringBuilder();

        sb.append("📋 我的任务列表\n\n");

        if (tasks == null || tasks.isEmpty()) {
            sb.append("暂无任务记录\n\n");
            sb.append("💡 提示:\n");
            sb.append("• 发送URL链接可创建网页抓取任务\n");
            sb.append("• 上传文件/图片可创建提取任务\n");
            sb.append("• 粘贴文本内容可创建文本提取任务\n");
            return sb.toString();
        }

        sb.append(String.format("共 %d 个任务\n\n", tasks.size()));

        // 按状态分组统计
        long pendingCount = tasks.stream().filter(t -> "PENDING".equals(t.getStatus())).count();
        long runningCount = tasks.stream().filter(t -> "RUNNING".equals(t.getStatus())).count();
        long successCount = tasks.stream().filter(t -> "SUCCESS".equals(t.getStatus())).count();
        long failedCount = tasks.stream().filter(t -> "FAILED".equals(t.getStatus())).count();

        sb.append("📊 状态统计:\n");
        sb.append(String.format("  ⏳ 等待执行: %d\n", pendingCount));
        sb.append(String.format("  🔄 执行中: %d\n", runningCount));
        sb.append(String.format("  ✅ 已完成: %d\n", successCount));
        sb.append(String.format("  ❌ 失败: %d\n\n", failedCount));

        sb.append("---\n\n");

        // 显示最近10个任务
        int displayCount = Math.min(tasks.size(), 10);
        sb.append(String.format("📝 最近 %d 个任务:\n\n", displayCount));

        for (int i = 0; i < displayCount; i++) {
            JobFetchTaskResponse task = tasks.get(i);
            sb.append(String.format("%d. **%s**\n", i + 1, task.getTaskId()));
            sb.append(String.format("   状态: %s %s\n", getStatusEmoji(task.getStatus()), getStatusText(task.getStatus())));

            if (task.getJobCount() != null && task.getJobCount() > 0) {
                sb.append(String.format("   职位数: %d 个\n", task.getJobCount()));
            }

            if (task.getCreateTime() != null) {
                sb.append(String.format("   创建时间: %s\n", task.getCreateTime().format(FORMATTER)));
            }

            sb.append("\n");
        }

        if (tasks.size() > 10) {
            sb.append(String.format("... 还有 %d 个任务未显示\n\n", tasks.size() - 10));
        }

        sb.append("💡 提示:\n\n");
        sb.append("• 使用 `/task <任务ID>` 可查看任务详情\n\n");
        sb.append("• 任务完成后会主动推送结果给您\n");

        return sb.toString();
    }

    /**
     * 构建任务状态消息
     */
    private String buildTaskStatusMessage(JobFetchTaskResponse task) {
        StringBuilder sb = new StringBuilder();

        // 标题
        sb.append("📋 任务状态查询结果\n\n");

        // 任务ID
        sb.append(String.format("**任务ID**: `%s`\n\n", task.getTaskId()));

        // 状态
        sb.append(String.format("**状态**: %s %s\n",
                getStatusEmoji(task.getStatus()),
                getStatusText(task.getStatus())));

        // 职位数量(如果已完成)
        if ("SUCCESS".equals(task.getStatus()) && task.getJobCount() != null) {
            sb.append(String.format("**提取职位数**: %d 个\n\n", task.getJobCount()));
        } else {
            sb.append("\n");
        }

        // 时间信息
        if (task.getCreateTime() != null) {
            sb.append(String.format("**创建时间**: %s\n", task.getCreateTime().format(FORMATTER)));
        }
        if (task.getFinishTime() != null) {
            sb.append(String.format("**完成时间**: %s\n", task.getFinishTime().format(FORMATTER)));
        }

        // 错误信息(如果失败)
        if ("FAILED".equals(task.getStatus()) && task.getErrorMessage() != null) {
            sb.append(String.format("\n**错误信息**: %s\n", task.getErrorMessage()));
        }

        // 操作提示
        sb.append("\n---\n💡 提示:\n\n");
        if ("PENDING".equals(task.getStatus()) || "RUNNING".equals(task.getStatus())) {
            sb.append("• 任务正在执行中，请稍后再次查询\n\n");
            sb.append(String.format("• 查询命令: `/task %s`\n\n", task.getTaskId()));
        } else if ("SUCCESS".equals(task.getStatus())) {
            sb.append("• 任务已完成，结果将主动推送给您\n\n");
            sb.append("• 如需重新抓取，请发送新的URL或文件\n\n");
        } else if ("FAILED".equals(task.getStatus())) {
            sb.append("• 任务执行失败，请检查输入是否正确\n\n");
            sb.append("• 可以重新发起任务尝试\n\n");
        }

        return sb.toString();
    }

    /**
     * 构建帮助消息
     */
    private String buildHelpMessage() {
        return """
                📋 任务查询命令
                                
                用法:
                • `/task` - 显示此帮助信息
                • `/task list` - 查询我的所有任务列表
                • `/task <任务ID>` - 查询指定任务的详细状态
                                
                示例:
                • `/task list` - 查看所有任务
                • `/task job_a1b2c3d4e5f6g7h8` - 查询指定任务的状态
                                
                💡 提示:
                • 创建任务时会返回任务ID
                • 可以通过任务ID随时查询进度
                • 任务完成后会主动通知您
                """;
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
