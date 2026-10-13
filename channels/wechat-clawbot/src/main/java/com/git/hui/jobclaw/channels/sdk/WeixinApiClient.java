package com.git.hui.jobclaw.channels.sdk;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Weixin API client for ClawBot integration.
 * Provides methods to interact with the Weixin iLink Bot API.
 */
public class WeixinApiClient {

    private static final Logger log = LoggerFactory.getLogger(WeixinApiClient.class);

    // Default timeouts (in milliseconds)
    private static final int DEFAULT_LONG_POLL_TIMEOUT_MS = 35000;
    private static final int DEFAULT_API_TIMEOUT_MS = 15000;
    private static final int DEFAULT_CONFIG_TIMEOUT_MS = 10000;

    private final String baseUrl;
    private final String token;
    private final ObjectMapper objectMapper;
    private final CloseableHttpClient httpClient;
    private final String channelVersion;

    public WeixinApiClient(String baseUrl, String token, String channelVersion) {
        this.baseUrl = ensureTrailingSlash(baseUrl);
        this.token = token;
        this.channelVersion = channelVersion != null ? channelVersion : "unknown";
        
        // 配置 ObjectMapper
        this.objectMapper = new ObjectMapper();
        this.objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        
        this.httpClient = HttpClients.createDefault();
    }

    /**
     * Build the base_info payload included in every API request.
     */
    public WeixinTypes.BaseInfo buildBaseInfo() {
        return new WeixinTypes.BaseInfo(channelVersion);
    }

    /**
     * Generate random X-WECHAT-UIN header value.
     * Random uint32 -> decimal string -> base64.
     */
    private String generateWechatUin() {
        SecureRandom random = new SecureRandom();
        long val = random.nextInt(Integer.MAX_VALUE);
        return Base64.getEncoder().encodeToString(String.valueOf(val).getBytes());
    }

    /**
     * Build common headers shared by all requests.
     */
    private Map<String, String> buildCommonHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("iLink-App-Id", ""); // Can be set from package.json if needed
        headers.put("iLink-App-ClientVersion", "0"); // Can be calculated from version
        return headers;
    }

    /**
     * Build headers for POST requests with authentication.
     */
    private Map<String, String> buildPostHeaders(String body) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("AuthorizationType", "ilink_bot_token");
        // Don't set Content-Length - HttpClient will add it automatically
        headers.put("X-WECHAT-UIN", generateWechatUin());
        
        if (token != null && !token.trim().isEmpty()) {
            headers.put("Authorization", "Bearer " + token.trim());
        }
        
        headers.putAll(buildCommonHeaders());
        return headers;
    }

    /**
     * Add headers to HTTP request.
     */
    private void addHeaders(org.apache.hc.core5.http.ClassicHttpRequest request, Map<String, String> headers) {
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            request.setHeader(entry.getKey(), entry.getValue());
        }
    }

    /**
     * Long-poll getUpdates. Server should hold the request until new messages or timeout.
     * 
     * On client-side timeout (no server response within timeoutMs), returns an empty response
     * with ret=0 so the caller can simply retry. This is normal for long-poll.
     */
    public WeixinTypes.GetUpdatesResp getUpdates(String getUpdatesBuf, Integer timeoutMs) throws Exception {
        int timeout = timeoutMs != null ? timeoutMs : DEFAULT_LONG_POLL_TIMEOUT_MS;
        
        try {
            WeixinTypes.GetUpdatesReq req = new WeixinTypes.GetUpdatesReq();
            req.setGetUpdatesBuf(getUpdatesBuf != null ? getUpdatesBuf : "");
            req.setBaseInfo(buildBaseInfo());

            String body = objectMapper.writeValueAsString(req);
            HttpPost request = new HttpPost(baseUrl + "ilink/bot/getupdates");
            addHeaders(request, buildPostHeaders(body));
            request.setEntity(new StringEntity(body));

            // getUpdates always returns JSON, don't treat as binary
            String rawText = executeRequest(request, timeout, false);
            return objectMapper.readValue(rawText, WeixinTypes.GetUpdatesResp.class);
            
        } catch (Exception e) {
            // Long-poll timeout is normal; return empty response so caller can retry
            if (e.getMessage() != null && e.getMessage().contains("timeout")) {
                log.debug("getUpdates: client-side timeout after {}ms, returning empty response", timeout);
                WeixinTypes.GetUpdatesResp resp = new WeixinTypes.GetUpdatesResp();
                resp.setRet(0);
                resp.setGetUpdatesBuf(getUpdatesBuf);
                return resp;
            }
            throw e;
        }
    }

    /**
     * Get a pre-signed CDN upload URL for a file.
     */
    public WeixinTypes.GetUploadUrlResp getUploadUrl(WeixinTypes.GetUploadUrlReq req) throws Exception {
        req.setBaseInfo(buildBaseInfo());
        
        String body = objectMapper.writeValueAsString(req);
        HttpPost request = new HttpPost(baseUrl + "ilink/bot/getuploadurl");
        addHeaders(request, buildPostHeaders(body));
        request.setEntity(new StringEntity(body));

        // getUploadUrl returns JSON
        String rawText = executeRequest(request, DEFAULT_API_TIMEOUT_MS, false);
        return objectMapper.readValue(rawText, WeixinTypes.GetUploadUrlResp.class);
    }

    /**
     * Send a single message downstream.
     */
    public void sendMessage(WeixinTypes.SendMessageReq req) throws Exception {
        req.setBaseInfo(buildBaseInfo());
        
        String body = objectMapper.writeValueAsString(req);
        HttpPost request = new HttpPost(baseUrl + "ilink/bot/sendmessage");
        addHeaders(request, buildPostHeaders(body));
        request.setEntity(new StringEntity(body));

        // sendMessage may return binary/empty response
        executeRequest(request, DEFAULT_API_TIMEOUT_MS, true);
    }

    /**
     * Fetch bot config (includes typing_ticket) for a given user.
     */
    public WeixinTypes.GetConfigResp getConfig(String ilinkUserId, String contextToken) throws Exception {
        Map<String, Object> bodyMap = new HashMap<>();
        bodyMap.put("ilink_user_id", ilinkUserId);
        bodyMap.put("context_token", contextToken);
        bodyMap.put("base_info", buildBaseInfo());

        String body = objectMapper.writeValueAsString(bodyMap);
        HttpPost request = new HttpPost(baseUrl + "ilink/bot/getconfig");
        addHeaders(request, buildPostHeaders(body));
        request.setEntity(new StringEntity(body));

        // getConfig returns JSON
        String rawText = executeRequest(request, DEFAULT_CONFIG_TIMEOUT_MS, false);
        return objectMapper.readValue(rawText, WeixinTypes.GetConfigResp.class);
    }

    /**
     * Send a typing indicator to a user.
     */
    public void sendTyping(WeixinTypes.SendTypingReq req) throws Exception {
        req.setBaseInfo(buildBaseInfo());
        
        String body = objectMapper.writeValueAsString(req);
        HttpPost request = new HttpPost(baseUrl + "ilink/bot/sendtyping");
        addHeaders(request, buildPostHeaders(body));
        request.setEntity(new StringEntity(body));

        // sendTyping may return binary/empty response
        executeRequest(request, DEFAULT_CONFIG_TIMEOUT_MS, true);
    }

    /**
     * Generate QR code for bot binding (bot_type=3 for ClawBot).
     *
     * @return QR code information including qrcode string and image content
     */
    public WeixinTypes.BindQrCodeResp getBotQrCode() throws Exception {
        org.apache.hc.client5.http.classic.methods.HttpGet request = 
                new org.apache.hc.client5.http.classic.methods.HttpGet(baseUrl + "ilink/bot/get_bot_qrcode?bot_type=3");
        request.setHeader("Content-Type", "application/json");
        request.setHeader("AuthorizationType", "ilink_bot_token");
        request.setHeader("X-WECHAT-UIN", generateWechatUin());
        request.setHeader("iLink-App-ClientVersion", channelVersion);

        String rawText = httpClient.execute(request, response -> {
            int statusCode = response.getCode();
            String text = EntityUtils.toString(response.getEntity());
            if (statusCode < 200 || statusCode >= 300) {
                throw new IOException("HTTP error " + statusCode + ": " + text);
            }
            return text;
        });

        return objectMapper.readValue(rawText, WeixinTypes.BindQrCodeResp.class);
    }

    /**
     * Check QR code binding status.
     *
     * @param qrCode The QR code string returned by getBotQrCode
     * @return QR code status response
     */
    public WeixinTypes.QrCodeStatusResp checkQrCodeStatus(String qrCode) throws Exception {
        if (qrCode == null || qrCode.isBlank()) {
            throw new IllegalArgumentException("QR code cannot be null or empty");
        }

        org.apache.hc.client5.http.classic.methods.HttpGet request = 
                new org.apache.hc.client5.http.classic.methods.HttpGet(
                        baseUrl + "ilink/bot/get_qrcode_status?qrcode=" + qrCode);
        request.setHeader("Content-Type", "application/json");
        request.setHeader("AuthorizationType", "ilink_bot_token");
        request.setHeader("iLink-App-ClientVersion", channelVersion);
        request.setHeader("X-WECHAT-UIN", generateWechatUin());

        String rawText = httpClient.execute(request, response -> {
            int statusCode = response.getCode();
            String text = EntityUtils.toString(response.getEntity());
            if (statusCode < 200 || statusCode >= 300) {
                throw new IOException("HTTP error " + statusCode + ": " + text);
            }
            return text;
        });

        return objectMapper.readValue(rawText, WeixinTypes.QrCodeStatusResp.class);
    }

    /**
     * Execute HTTP request and return response text.
     * 
     * @param request HTTP request
     * @param timeoutMs Timeout in milliseconds
     * @param allowBinaryResponse If true, treat octet-stream/empty content-type as success (for send operations)
     */
    private String executeRequest(org.apache.hc.core5.http.ClassicHttpRequest request, int timeoutMs, boolean allowBinaryResponse) throws IOException {
        return httpClient.execute(request, response -> {
            int statusCode = response.getCode();
            String contentType = response.getHeader("Content-Type") != null
                    ? response.getHeader("Content-Type").getValue()
                    : "";
            
            // Handle binary/empty responses (only for send operations)
            if (allowBinaryResponse && (contentType.contains("octet-stream") || contentType.isEmpty())) {
                EntityUtils.consume(response.getEntity());
                if (statusCode >= 200 && statusCode < 300) {
                    return "{}";
                }
                throw new IOException("HTTP error: " + statusCode);
            }

            // For JSON responses, always parse the body
            String rawText = EntityUtils.toString(response.getEntity());
            log.debug("Response status={} body={}", statusCode, rawText);
            
            if (statusCode < 200 || statusCode >= 300) {
                throw new IOException("HTTP error " + statusCode + ": " + rawText);
            }
            
            return rawText;
        });
    }

    /**
     * Ensure URL ends with trailing slash.
     */
    private String ensureTrailingSlash(String url) {
        return url.endsWith("/") ? url : url + "/";
    }

    /**
     * Close the HTTP client.
     */
    public void close() throws IOException {
        httpClient.close();
    }
}
