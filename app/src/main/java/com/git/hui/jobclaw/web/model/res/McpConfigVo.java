package com.git.hui.jobclaw.web.model.res;

import java.util.Map;

/**
 * mcp服务配置
 *
 * @date 2025/7/28
 */
public record McpConfigVo(String type, String url, String version, Map<String, String> headers) {
}
