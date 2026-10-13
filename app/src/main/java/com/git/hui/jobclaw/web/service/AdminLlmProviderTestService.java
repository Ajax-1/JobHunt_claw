package com.git.hui.jobclaw.web.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.git.hui.jobclaw.web.model.res.AdminLlmProviderTestVo;
import com.git.hui.jobclaw.web.model.res.AdminLlmProviderVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Admin 大模型供应商连通性测试。
 *
 * @date 2026/6/26
 */
@Slf4j
@Service
public class AdminLlmProviderTestService {
    private static final String DEFAULT_COMPLETIONS_PATH = "/v1/chat/completions";
    private static final String DEFAULT_ANTHROPIC_MESSAGES_PATH = "/v1/messages";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(4);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(8);
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .build();

    private final ObjectMapper objectMapper = new ObjectMapper();

    public AdminLlmProviderTestVo test(String provider, AdminLlmProviderVo.ProviderConfigVo config, String apiKey) {
        long started = System.nanoTime();
        AdminLlmProviderVo.ModelConfigVo modelConfig = pickTestModel(config.getModels());
        String apiStyle = trim(config.getApiStyle()).toLowerCase(Locale.ROOT);
        String modelName = trim(modelConfig.getName());

        AdminLlmProviderTestVo vo = baseResult(provider, apiStyle, modelName, started);
        if (!isSupportedApiStyle(apiStyle)) {
            vo.setMessage("连接失败：测试连接只支持 OpenAI 兼容和 Anthropic。");
            return vo;
        }

        if (!StringUtils.hasText(apiKey)) {
            vo.setMessage("连接失败：API Key 为空，请检查 API Key。");
            return vo;
        }
        if (!StringUtils.hasText(config.getBaseUrl())) {
            vo.setMessage("连接失败：Base URL 为空，请检查 Base URL。");
            return vo;
        }
        if (!StringUtils.hasText(modelName)) {
            vo.setMessage("连接失败：模型名为空，请先配置一个可用模型。");
            return vo;
        }

        try {
            HttpRequest request = buildRequest(apiStyle, config, apiKey, modelName);
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            applyHttpResult(vo, response);
        } catch (Exception e) {
            log.warn("LLM provider connection test failed, provider={}, apiStyle={}, model={}", provider, apiStyle, modelName, e);
            TestHint hint = friendlyHint(e);
            vo.setStatus(hint.status());
            vo.setMessage(hint.message());
        }
        vo.setElapsedMs(elapsedMs(started));
        return vo;
    }

    private AdminLlmProviderVo.ModelConfigVo pickTestModel(List<AdminLlmProviderVo.ModelConfigVo> models) {
        if (models == null || models.isEmpty()) {
            return new AdminLlmProviderVo.ModelConfigVo();
        }
        return models.stream()
                .filter(model -> StringUtils.hasText(model.getName()) && "TEXT".equalsIgnoreCase(model.getType()))
                .findFirst()
                .orElseGet(() -> models.stream()
                        .filter(model -> StringUtils.hasText(model.getName()))
                        .findFirst()
                        .orElseGet(AdminLlmProviderVo.ModelConfigVo::new));
    }

    private boolean isSupportedApiStyle(String apiStyle) {
        return "openai".equals(apiStyle) || "anthropic".equals(apiStyle);
    }

    private HttpRequest buildRequest(String apiStyle, AdminLlmProviderVo.ProviderConfigVo config, String apiKey, String modelName)
            throws JsonProcessingException {
        String path = resolveCompletionsPath(apiStyle, config.getCompletionsPath());
        HttpRequest.Builder builder = HttpRequest.newBuilder(buildEndpoint(config.getBaseUrl(), path))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(buildBody(apiStyle, modelName), StandardCharsets.UTF_8));

        if ("anthropic".equals(apiStyle)) {
            builder.header("x-api-key", apiKey)
                    .header("anthropic-version", "2023-06-01");
        } else {
            builder.header("Authorization", "Bearer " + apiKey);
        }
        return builder.build();
    }

    private String buildBody(String apiStyle, String modelName) throws JsonProcessingException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", modelName);
        body.put("max_tokens", 1);
        body.put("messages", List.of(Map.of("role", "user", "content", "ping")));
        if (!"anthropic".equals(apiStyle)) {
            body.put("stream", false);
        }
        return objectMapper.writeValueAsString(body);
    }

    private URI buildEndpoint(String baseUrl, String path) {
        String normalizedBaseUrl = trim(baseUrl);
        String normalizedPath = trim(path);
        if (normalizedBaseUrl.endsWith("/") && normalizedPath.startsWith("/")) {
            return URI.create(normalizedBaseUrl.substring(0, normalizedBaseUrl.length() - 1) + normalizedPath);
        }
        if (!normalizedBaseUrl.endsWith("/") && !normalizedPath.startsWith("/")) {
            return URI.create(normalizedBaseUrl + "/" + normalizedPath);
        }
        return URI.create(normalizedBaseUrl + normalizedPath);
    }

    private String resolveCompletionsPath(String apiStyle, String completionsPath) {
        if (StringUtils.hasText(completionsPath)) {
            return completionsPath.trim();
        }
        return "anthropic".equals(apiStyle) ? DEFAULT_ANTHROPIC_MESSAGES_PATH : DEFAULT_COMPLETIONS_PATH;
    }

    private void applyHttpResult(AdminLlmProviderTestVo vo, HttpResponse<String> response) {
        int statusCode = response.statusCode();
        if (statusCode >= 200 && statusCode < 300) {
            vo.setSuccess(true);
            vo.setStatus("success");
            vo.setMessage("连接成功：API Key、Base URL 和模型配置可用。");
            return;
        }

        TestHint hint = friendlyHttpHint(statusCode, response.body());
        vo.setStatus(hint.status());
        vo.setMessage(hint.message());
    }

    private AdminLlmProviderTestVo baseResult(String provider, String apiStyle, String modelName, long started) {
        AdminLlmProviderTestVo vo = new AdminLlmProviderTestVo();
        vo.setSuccess(false);
        vo.setStatus("error");
        vo.setProvider(provider);
        vo.setApiStyle(apiStyle);
        vo.setModel(modelName);
        vo.setElapsedMs(elapsedMs(started));
        return vo;
    }

    private long elapsedMs(long started) {
        return (System.nanoTime() - started) / 1_000_000;
    }

    // AIDEV-NOTE: AI-GENERATED test hint mapping
    private TestHint friendlyHint(Exception e) {
        String text = collectExceptionText(e).toLowerCase();
        if (e instanceof InterruptedException) {
            Thread.currentThread().interrupt();
            return error("连接失败：测试请求被中断，请稍后重试。");
        }
        if (e instanceof IllegalArgumentException) {
            return error("连接失败：Base URL 格式不正确，请检查协议和域名。");
        }
        if (e instanceof IOException && (e instanceof HttpTimeoutException || hasCause(e, HttpTimeoutException.class)
                || hasCause(e, ConnectException.class))) {
            return error("连接失败：Base URL 无法访问或响应超时，请检查域名、协议和网络连通性。");
        }
        if (text.contains("401") || text.contains("unauthorized") || text.contains("authentication")
                || text.contains("invalid api key") || text.contains("api key is invalid")
                || text.contains("incorrect api key")) {
            return error("连接失败：API Key 无效或没有该模型权限，请检查 API Key。");
        }
        if (text.contains("403") || text.contains("forbidden") || text.contains("permission")) {
            return error("连接失败：API Key 权限不足或账号不可用，请检查 API Key 权限。");
        }
        if (text.contains("429") || text.contains("rate limit") || text.contains("too many requests")
                || text.contains("访问量过大") || text.contains("限流")) {
            return warning("已连到供应商，但模型当前限流或繁忙。API Key 和 Base URL 已通过基础连通性验证，请稍后重试或换一个模型测试。");
        }
        if (text.contains("404") || text.contains("not found") || text.contains("no static resource")) {
            return error("连接失败：Base URL、接口路径或模型名不正确，请检查 Base URL 和模型名。");
        }
        if (text.contains("unknownhost") || text.contains("connection refused") || text.contains("connect timed out")
                || text.contains("read timed out") || text.contains("timeout") || text.contains("sslhandshake")
                || hasCause(e, UnknownHostException.class) || hasCause(e, SocketTimeoutException.class)) {
            return error("连接失败：Base URL 无法访问，请检查域名、协议和网络连通性。");
        }
        if (text.contains("model")) {
            return error("连接失败：模型名不可用或没有权限，请检查模型配置。");
        }
        return error("连接失败：供应商返回异常，请检查 API Key、Base URL 和模型名。");
    }

    private TestHint friendlyHttpHint(int statusCode, String body) {
        String responseBody = trimBody(body);
        if (statusCode == 401) {
            return error("连接失败：供应商返回 401，API Key 无效或没有该模型权限，请检查 API Key。");
        }
        if (statusCode == 403) {
            return error("连接失败：供应商返回 403，API Key 权限不足或账号不可用，请检查 API Key 权限。");
        }
        if (statusCode == 404) {
            return error("连接失败：供应商返回 404，Base URL、接口路径或模型名不正确，请检查 Base URL 和模型名。");
        }
        if (statusCode == 429) {
            return warning("已连到供应商，但供应商返回 429，当前模型限流或繁忙。API Key 和 Base URL 已通过基础连通性验证，请稍后重试或换一个模型测试。");
        }
        if (statusCode >= 400 && statusCode < 500) {
            return error("连接失败：供应商返回 " + statusCode + "，请检查 API Key、Base URL 和模型名。" + responseBody);
        }
        if (statusCode >= 500) {
            return warning("已连到供应商，但供应商返回 " + statusCode + "。Base URL 可访问，请稍后重试或换一个模型测试。" + responseBody);
        }
        return error("连接失败：供应商返回异常状态 " + statusCode + "。" + responseBody);
    }

    private String collectExceptionText(Throwable e) {
        StringBuilder builder = new StringBuilder();
        Throwable current = e;
        while (current != null) {
            builder.append(current.getClass().getSimpleName()).append(' ');
            if (StringUtils.hasText(current.getMessage())) {
                builder.append(current.getMessage()).append(' ');
            }
            current = current.getCause();
        }
        return builder.toString();
    }

    private boolean hasCause(Throwable e, Class<? extends Throwable> causeType) {
        Throwable current = e;
        while (current != null) {
            if (causeType.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private String trimBody(String body) {
        if (!StringUtils.hasText(body)) {
            return "";
        }
        String trimmed = body.replaceAll("\\s+", " ").trim();
        if (trimmed.length() > 120) {
            trimmed = trimmed.substring(0, 120) + "...";
        }
        return " 返回：" + trimmed;
    }

    private TestHint warning(String message) {
        return new TestHint("warning", message);
    }

    private TestHint error(String message) {
        return new TestHint("error", message);
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private record TestHint(String status, String message) {
    }
}
