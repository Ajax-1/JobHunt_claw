package com.git.hui.jobclaw.agents.jobfetch.service;

import com.git.hui.jobclaw.agents.jobfetch.crawler.JobCrawler;
import com.git.hui.jobclaw.agents.jobfetch.extract.JobExtractor;
import com.git.hui.jobclaw.agents.jobfetch.extract.impl.TextJobExtractor;
import com.git.hui.jobclaw.agents.jobfetch.service.model.FetchedJobInfo;
import com.git.hui.jobclaw.agents.jobfetch.service.model.JobFetchTaskEntity;
import com.git.hui.jobclaw.agents.jobfetch.service.model.JobFetchTaskResponse;
import com.git.hui.jobclaw.agents.jobfetch.service.model.JobFetchTaskStatus;
import com.git.hui.jobclaw.agents.jobfetch.service.repository.JobFetchTaskRepository;
import com.git.hui.jobclaw.core.agent.models.UserConversationInfo;
import com.git.hui.jobclaw.core.bus.ChannelEventPublisher;
import com.git.hui.jobclaw.core.channel.ChannelReceiveMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 职位抓取任务服务
 *
 * @date 2026/4/20
 */
@Slf4j
@Service
public class JobFetchTaskService {

    @Autowired
    private JobFetchTaskRepository taskRepository;

    @Autowired
    private JobCrawler jobCrawler;

    @Autowired
    private List<JobExtractor> jobExtractorList;

    @Autowired
    private ChannelEventPublisher channelEventPublisher;

    @Autowired
    private JobInfoPersistService jobInfoSaveService;

    /**
     * 创建URL抓取任务
     */
    public JobFetchTaskResponse createUrlTask(UserConversationInfo userConversationInfo,
                                              String url,
                                              ChannelReceiveMessage msg) {
        String taskId = generateTaskId();

        // 创建任务记录
        JobFetchTaskEntity task = new JobFetchTaskEntity()
                .setTaskId(taskId)
                .setJobClawUserId(userConversationInfo.jobClawUserId())
                .setChannel(userConversationInfo.channel())
                .setConversionId(userConversationInfo.conversationId())
                .setTaskType("URL")
                .setInputContent(url)
                .setOriginMessage(msg.getMessage())
                .setStatus(JobFetchTaskStatus.PENDING.name())
                .setJobCount(0);

        taskRepository.save(task);
        log.info("创建URL抓取任务: taskId={}, url={}", taskId, url);

        // 异步执行任务
        Thread.ofVirtual().start(() -> executeUrlTaskAsync(task, userConversationInfo, url, msg));
        return buildTaskResponse(task);
    }

    /**
     * 创建文本/文件提取任务
     */
    public JobFetchTaskResponse createTextOrFileTask(UserConversationInfo userConversationInfo,
                                                     String text,
                                                     String path,
                                                     ChannelReceiveMessage msg) {
        String taskId = generateTaskId();

        // 创建任务记录
        JobFetchTaskEntity task = new JobFetchTaskEntity()
                .setTaskId(taskId)
                .setJobClawUserId(userConversationInfo.jobClawUserId())
                .setChannel(userConversationInfo.channel())
                .setConversionId(userConversationInfo.conversationId())
                .setTaskType(CollectionUtils.isEmpty(msg.getFiles()) && CollectionUtils.isEmpty(msg.getMedias()) ? "TEXT" : "FILE")
                .setInputContent(text != null ? text : path)
                .setOriginMessage(msg.getMessage())
                .setStatus(JobFetchTaskStatus.PENDING.name())
                .setJobCount(0);

        taskRepository.save(task);
        log.info("创建文本/文件提取任务: taskId={}, type={}", taskId, task.getTaskType());

        // 异步执行任务
        Thread.ofVirtual().start(() -> executeTextOrFileTaskAsync(task, userConversationInfo, text, path, msg));
        return buildTaskResponse(task);
    }

    /**
     * 查询任务状态
     */
    public JobFetchTaskResponse queryTask(String jobClawUserId, String taskId) {
        JobFetchTaskEntity task = taskRepository.findByJobClawUserIdAndTaskId(jobClawUserId, taskId)
                .orElseThrow(() -> new IllegalArgumentException("任务不存在: " + taskId));

        return buildTaskResponse(task);
    }

    /**
     * 查询用户的任务列表(按创建时间倒序)
     */
    public List<JobFetchTaskResponse> listTasks(String jobClawUserId) {
        List<JobFetchTaskEntity> tasks = taskRepository.findByJobClawUserIdOrderByCreateTimeDesc(jobClawUserId);
        return tasks.stream()
                .map(this::buildTaskResponse)
                .toList();
    }

    /**
     * 异步执行URL抓取任务
     */
    @Async
    public void executeUrlTaskAsync(JobFetchTaskEntity task,
                                    UserConversationInfo userConversationInfo,
                                    String url,
                                    ChannelReceiveMessage msg) {
        String taskId = task.getTaskId();
        try {
            // 更新状态为运行中
            updateTaskStatus(task, JobFetchTaskStatus.RUNNING, null);

            log.info("开始执行URL抓取任务: {}", taskId);

            // 执行抓取
            List<FetchedJobInfo> jobs = jobCrawler.crawl(userConversationInfo, url, msg.getMessage());

            // 更新任务成功
            int jobCount = jobs != null ? jobs.size() : 0;
            updateTaskSuccess(task, jobCount);

            log.info("URL抓取任务完成: taskId={}, jobCount={}", taskId, jobCount);

            // TODO: 推送结果给用户(SSE或WebSocket)
            pushTaskResult(task, jobs);

        } catch (Exception e) {
            log.error("URL抓取任务失败: taskId={}", taskId, e);
            updateTaskFailed(task, e.getMessage());
        }
    }

    /**
     * 异步执行文本/文件提取任务
     */
    @Async
    public void executeTextOrFileTaskAsync(JobFetchTaskEntity task,
                                           UserConversationInfo userConversationInfo,
                                           String text,
                                           String path,
                                           ChannelReceiveMessage msg) {
        String taskId = task.getTaskId();
        try {
            // 更新状态为运行中
            updateTaskStatus(task, JobFetchTaskStatus.RUNNING, null);

            log.info("开始执行文本/文件提取任务: {}", taskId);

            // 选择合适的提取器
            JobExtractor extractor = selectExtractor(msg);
            if (extractor == null) {
                throw new IllegalStateException("未找到合适的提取器");
            }

            // 执行提取
            List<FetchedJobInfo> jobs = extractor.extractFromInput(userConversationInfo, msg);

            // 更新任务成功
            int jobCount = jobs != null ? jobs.size() : 0;
            updateTaskSuccess(task, jobCount);

            log.info("文本/文件提取任务完成: taskId={}, jobCount={}", taskId, jobCount);

            // TODO: 推送结果给用户(SSE或WebSocket)
            pushTaskResult(task, jobs);

        } catch (Exception e) {
            log.error("文本/文件提取任务失败: taskId={}", taskId, e);
            updateTaskFailed(task, e.getMessage());
        }
    }

    /**
     * 选择提取器
     */
    private JobExtractor selectExtractor(ChannelReceiveMessage msg) {
        if (!CollectionUtils.isEmpty(msg.getFiles())) {
            var file = msg.getFiles().get(0);
            for (JobExtractor extractor : jobExtractorList) {
                if (extractor.supports(file.getMimeType())) {
                    return extractor;
                }
            }
        } else if (!CollectionUtils.isEmpty(msg.getMedias())) {
            var media = msg.getMedias().get(0);
            for (JobExtractor extractor : jobExtractorList) {
                if (extractor.supports(media.getMimeType())) {
                    return extractor;
                }
            }
        }

        // 默认使用文本提取器
        for (JobExtractor extractor : jobExtractorList) {
            if (extractor instanceof TextJobExtractor) {
                return extractor;
            }
        }
        return null;
    }

    /**
     * 更新任务状态
     */
    private void updateTaskStatus(JobFetchTaskEntity task, JobFetchTaskStatus status, String errorMessage) {
        task.setStatus(status.name());
        task.setErrorMessage(errorMessage);
        if (status == JobFetchTaskStatus.RUNNING) {
            task.setStartTime(LocalDateTime.now());
        }
        taskRepository.save(task);
    }

    /**
     * 更新任务成功
     */
    private void updateTaskSuccess(JobFetchTaskEntity task, int jobCount) {
        task.setStatus(JobFetchTaskStatus.SUCCESS.name());
        task.setJobCount(jobCount);
        task.setFinishTime(LocalDateTime.now());
        taskRepository.save(task);
    }

    /**
     * 更新任务失败
     */
    private void updateTaskFailed(JobFetchTaskEntity task, String errorMessage) {
        task.setStatus(JobFetchTaskStatus.FAILED.name());
        task.setErrorMessage(errorMessage);
        task.setFinishTime(LocalDateTime.now());
        taskRepository.save(task);
    }

    /**
     * 构建任务响应
     */
    private JobFetchTaskResponse buildTaskResponse(JobFetchTaskEntity task) {
        return JobFetchTaskResponse.builder()
                .taskId(task.getTaskId())
                .status(task.getStatus())
                .jobCount(task.getJobCount())
                .errorMessage(task.getErrorMessage())
                .createTime(task.getCreateTime())
                .finishTime(task.getFinishTime())
                .build();
    }

    /**
     * 生成任务ID
     * 基于时间戳转换为36进制(a-z0-9)字符串
     */
    private String generateTaskId() {
        long timestamp = System.currentTimeMillis() / 1000;
        return "job_" + encodeBase36(timestamp);
    }

    /**
     * 将长整数转换为36进制字符串(a-z0-9)
     *
     * @param number 长整数
     * @return 36进制字符串
     */
    private String encodeBase36(long number) {
        if (number == 0) {
            return "0";
        }

        StringBuilder sb = new StringBuilder();
        String chars = "0123456789abcdefghijklmnopqrstuvwxyz";
        boolean negative = number < 0;

        if (negative) {
            number = -number;
        }

        while (number > 0) {
            int remainder = (int) (number % 36);
            sb.append(chars.charAt(remainder));
            number /= 36;
        }

        if (negative) {
            sb.append('-');
        }

        return sb.reverse().toString();
    }

    /**
     * 推送任务结果
     */
    private void pushTaskResult(JobFetchTaskEntity task, List<FetchedJobInfo> jobs) {
        log.info("任务结果就绪,待推送: taskId={}, jobCount={}", task.getTaskId(),
                jobs != null ? jobs.size() : 0);
    
        // 保存职位信息并获取统计结果
        if (jobs != null && !jobs.isEmpty()) {
            JobInfoPersistService.SaveRes res = jobInfoSaveService.save(jobs);
                
            // 构建友好的提示消息
            String resultMessage = buildTaskCompletionMessage(task, res);
                
            log.info("推送任务完成通知: taskId={}, message={}", task.getTaskId(), resultMessage);
                
            channelEventPublisher.publishProactiveMessage("JOB_TASK_" + task.getTaskId(),
                    task.getJobClawUserId(),
                    task.getChannel(),
                    resultMessage
            );
        } else {
            // 没有提取到职位信息
            String emptyMessage = buildEmptyResultMessage(task);
            channelEventPublisher.publishProactiveMessage("JOB_TASK_" + task.getTaskId(),
                    task.getJobClawUserId(),
                    task.getChannel(),
                    emptyMessage
            );
        }
    }
    
    /**
     * 构建任务完成的友好提示消息
     */
    private String buildTaskCompletionMessage(JobFetchTaskEntity task, JobInfoPersistService.SaveRes res) {
        StringBuilder sb = new StringBuilder();
            
        sb.append("✅ 任务执行完成\n\n");
        sb.append(String.format("📋 任务ID: `%s`\n\n", task.getTaskId()));
        sb.append(String.format("📊 提取职位数: %d 个\n\n", res.insertCnt() + res.updateCnt()));
            
        // 详细统计
        sb.append("📈 数据处理结果:\n\n");
        sb.append(String.format("  • 新增: %d 条\n\n", res.insertCnt()));
        sb.append(String.format("  • 更新: %d 条\n\n", res.updateCnt()));
            
        sb.append("\n💡 提示:\n\n");
        sb.append("• 职位信息已保存到数据库\n\n");
        sb.append("• 您可以在管理后台查看和管理这些职位\n\n");
        sb.append("• 如需继续抓取，请发送新的URL或文件\n\n");
            
        return sb.toString();
    }
    
    /**
     * 构建空结果的提示消息
     */
    private String buildEmptyResultMessage(JobFetchTaskEntity task) {
        StringBuilder sb = new StringBuilder();
            
        sb.append("⚠️ 任务执行完成，但未提取到职位信息\n\n");
        sb.append(String.format("📋 任务ID: `%s`\n\n", task.getTaskId()));
        sb.append("可能的原因:\n\n");
        sb.append("• 网页中没有找到符合格式的职位信息\n\n");
        sb.append("• 文本内容中不包含有效的招聘数据\n\n");
        sb.append("• 文件格式不支持或内容为空\n\n");
        sb.append("💡 建议:\n\n");
        sb.append("• 检查URL是否正确指向招聘页面\n\n");
        sb.append("• 确认文本/文件中包含完整的招聘信息\n\n");
        sb.append("• 尝试其他来源或格式\n\n");
            
        return sb.toString();
    }
}
