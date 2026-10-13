package com.git.hui.jobclaw.agents.jobfetch.service.model;

/**
 * 职位抓取任务状态枚举
 *
 * @date 2026/4/20
 */
public enum JobFetchTaskStatus {
    /**
     * 待执行
     */
    PENDING,
    
    /**
     * 执行中
     */
    RUNNING,
    
    /**
     * 成功
     */
    SUCCESS,
    
    /**
     * 失败
     */
    FAILED
}
