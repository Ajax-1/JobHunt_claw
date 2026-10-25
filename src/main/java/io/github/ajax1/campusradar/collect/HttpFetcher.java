package io.github.ajax1.campusradar.collect;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 一次采集任务一个实例：自带 cookie（字节、阿里需要先拿 csrf），并在请求之间强制留出间隔，避免给官网造成压力。
 */
public class HttpFetcher {

    static final String USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/130.0 Safari/537.36";
    private static final int MAX_ATTEMPTS = 3;

    private final HttpClient client;
    private final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
    private final long minIntervalMs;
    private long lastRequestAt;
    private int requestCount;

    public HttpFetcher(long minIntervalMs) {
        this.minIntervalMs = minIntervalMs;
        this.client = HttpClient.newBuilder()
                .cookieHandler(cookies)
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public String get(String url, Map<String, String> headers) {
        return send(base(url, headers).GET().build());
    }

    public String postJson(String url, String json, Map<String, String> headers) {
        Map<String, String> h = new LinkedHashMap<>(headers);
        h.putIfAbsent("Content-Type", "application/json");
        return send(base(url, h).POST(HttpRequest.BodyPublishers.ofString(json)).build());
    }

    public String postForm(String url, Map<String, String> form, Map<String, String> headers) {
        String body = form.entrySet().stream()
                .map(e -> enc(e.getKey()) + "=" + enc(e.getValue()))
                .collect(Collectors.joining("&"));
        Map<String, String> h = new LinkedHashMap<>(headers);
        h.putIfAbsent("Content-Type", "application/x-www-form-urlencoded");
        return send(base(url, h).POST(HttpRequest.BodyPublishers.ofString(body)).build());
    }

    /**
     * 读取已经收到的 cookie，如阿里的 XSRF-TOKEN
     */
    public String cookie(String url, String name) {
        return cookies.getCookieStore().get(URI.create(url)).stream()
                .filter(c -> c.getName().equals(name))
                .map(HttpCookie::getValue)
                .findFirst()
                .orElse(null);
    }

    public int requestCount() {
        return requestCount;
    }

    private HttpRequest.Builder base(String url, Map<String, String> headers) {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json, text/plain, */*")
                .header("Accept-Language", "zh-CN,zh;q=0.9");
        headers.forEach(b::header);
        return b;
    }

    private String send(HttpRequest request) {
        CollectException last = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            pace();
            try {
                requestCount++;
                HttpResponse<String> resp = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                int code = resp.statusCode();
                if (code >= 200 && code < 300) {
                    return resp.body();
                }
                last = new CollectException("HTTP " + code + " " + request.uri() + " " + snippet(resp.body()));
                // 4xx 一般是参数或风控问题，重试没有意义
                if (code < 500) {
                    throw last;
                }
            } catch (IOException e) {
                last = new CollectException("请求失败 " + request.uri() + ": " + e.getMessage(), e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new CollectException("请求被中断 " + request.uri(), e);
            }
            sleep(1000L * attempt);
        }
        throw last;
    }

    private void pace() {
        long wait = lastRequestAt + minIntervalMs - System.currentTimeMillis();
        if (wait > 0) {
            sleep(wait);
        }
        lastRequestAt = System.currentTimeMillis();
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CollectException("等待被中断", e);
        }
    }

    private static String enc(String s) {
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
    }

    private static String snippet(String body) {
        if (body == null) {
            return "";
        }
        String s = body.replaceAll("\\s+", " ");
        return s.length() > 200 ? s.substring(0, 200) : s;
    }
}
