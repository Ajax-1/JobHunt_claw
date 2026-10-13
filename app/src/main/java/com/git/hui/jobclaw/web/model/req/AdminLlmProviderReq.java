package com.git.hui.jobclaw.web.model.req;

import com.git.hui.jobclaw.web.model.res.AdminLlmProviderVo;
import lombok.Data;

/**
 * Admin 全局大模型供应商配置请求。
 *
 * @date 2026/6/18
 */
@Data
public class AdminLlmProviderReq {
    private String provider;
    private String originalProvider;
    private AdminLlmProviderVo.ProviderConfigVo config;
}
