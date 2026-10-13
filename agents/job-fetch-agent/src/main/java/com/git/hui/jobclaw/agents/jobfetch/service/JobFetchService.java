package com.git.hui.jobclaw.agents.jobfetch.service;

import com.git.hui.jobclaw.agents.jobfetch.service.model.JobFetchTaskResponse;
import com.git.hui.jobclaw.core.agent.models.UserConversationInfo;
import com.git.hui.jobclaw.core.channel.ChannelReceiveMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 职位抓取服务(异步任务版)
 *
 * @date 2026/4/20
 */
@Slf4j
@Service
public class JobFetchService {

    @Autowired
    private JobFetchTaskService taskService;

    /**
     * 从URL抓取职位(异步)
     * @return 任务ID, 用户可通过任务ID查询进度
     */
    public JobFetchTaskResponse fetchFromUrl(UserConversationInfo userConversationInfo,
                                             String url,
                                             ChannelReceiveMessage msg) {
        log.info("创建URL抓取任务: url={}", url);
        return taskService.createUrlTask(userConversationInfo, url, msg);
    }

    /**
     * 从文本或本地文件提取职位(异步)
     * @return 任务ID, 用户可通过任务ID查询进度
     */
    public JobFetchTaskResponse fetchFromTextOrLocalFile(UserConversationInfo userConversationInfo,
                                                         String text,
                                                         String path,
                                                         ChannelReceiveMessage msg) {
        log.info("创廾文本/文件提取任务");
        return taskService.createTextOrFileTask(userConversationInfo, text, path, msg);
    }

    /**
     * 查询任务状态
     */
    public JobFetchTaskResponse queryTask(String jobClawUserId, String taskId) {
        return taskService.queryTask(jobClawUserId, taskId);
    }

    /**
     * 查询用户的任务列表
     */
    public List<JobFetchTaskResponse> listTasks(String jobClawUserId) {
        return taskService.listTasks(jobClawUserId);
    }
}
