package io.github.ajax1.campusradar.push;

import io.github.ajax1.campusradar.RadarProperties;
import io.github.ajax1.campusradar.collect.Json;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/**
 * 企业微信群机器人：POST JSON 到 webhook，返回 errcode = 0 表示成功。
 * 只能单向发消息，收不到群里的回复，所以「选中某个岗位」要通过 MCP 或 REST 完成。
 */
@Component
public class WeComPushChannel implements PushChannel {

    /**
     * 企业微信 markdown 消息正文上限 4096 字节
     */
    static final int MAX_BYTES = 4096;

    private final String webhook;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public WeComPushChannel(RadarProperties properties) {
        this.webhook = properties.push().wecomWebhook();
    }

    @Override
    public boolean enabled() {
        return webhook != null && !webhook.isBlank();
    }

    @Override
    public void sendMarkdown(String markdown) {
        if (!enabled()) {
            throw new IllegalStateException("没有配置企业微信 webhook（RADAR_WECOM_WEBHOOK）");
        }
        if (markdown.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw new IllegalArgumentException("消息超过企业微信 4096 字节上限");
        }
        String body = Json.write(Map.of("msgtype", "markdown", "markdown", Map.of("content", markdown)));
        HttpRequest request = HttpRequest.newBuilder(URI.create(webhook))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        try {
            HttpResponse<String> resp = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            JsonNode root = Json.parse(resp.body());
            // webhook 地址里带 key，报错信息里不能出现它
            if (resp.statusCode() != 200 || root.path("errcode").asInt(-1) != 0) {
                throw new IllegalStateException("企业微信推送失败: HTTP " + resp.statusCode() + " "
                        + Json.text(root, "errcode") + " " + Json.text(root, "errmsg"));
            }
        } catch (IOException e) {
            throw new IllegalStateException("企业微信推送请求失败: " + e.getClass().getSimpleName(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("企业微信推送被中断", e);
        }
    }
}
