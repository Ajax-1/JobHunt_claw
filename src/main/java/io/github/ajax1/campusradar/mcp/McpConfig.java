package io.github.ajax1.campusradar.mcp;

import io.github.ajax1.campusradar.RadarProperties;
import io.github.ajax1.campusradar.security.AccessFilter;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class McpConfig {

    @Bean
    public ToolCallbackProvider jobToolCallbacks(JobTools jobTools) {
        return MethodToolCallbackProvider.builder().toolObjects(jobTools).build();
    }

    /**
     * 配置了 radar.mcp.token 时 /mcp 和 /api/** 都要带令牌；管理接口无论如何只允许直连本机
     */
    @Bean
    public FilterRegistrationBean<AccessFilter> accessFilter(RadarProperties properties) {
        String token = properties.mcp() == null ? null : properties.mcp().token();
        FilterRegistrationBean<AccessFilter> bean = new FilterRegistrationBean<>(new AccessFilter(token));
        bean.addUrlPatterns("/mcp", "/mcp/*", "/api/*");
        return bean;
    }
}
