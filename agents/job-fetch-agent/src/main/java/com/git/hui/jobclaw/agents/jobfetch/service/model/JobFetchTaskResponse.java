package com.git.hui.jobclaw.agents.jobfetch.service.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 任务响应DTO
 *
 * @date 2026/4/20
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobFetchTaskResponse {
    
    /**
     * 任务ID
     */
    private String taskId;
    
    /**
     * 任务状态
     */
    private String status;
    
    /**
     * 职位数量
     */
    private Integer jobCount;
    
    /**
     * 错误信息
     */
    private String errorMessage;
    
    /**
     * 创建时间
     */
    private LocalDateTime createTime;
    
    /**
     * 完成时间
     */
    private LocalDateTime finishTime;
}
