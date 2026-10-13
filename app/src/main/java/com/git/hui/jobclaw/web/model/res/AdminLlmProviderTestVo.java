package com.git.hui.jobclaw.web.model.res;

import lombok.Data;

/**
 * Admin 大模型供应商连通性测试结果。
 *
 * @date 2026/6/26
 */
@Data
public class AdminLlmProviderTestVo {
    private boolean success;
    private String status;
    private String message;
    private String provider;
    private String apiStyle;
    private String model;
    private long elapsedMs;
}
