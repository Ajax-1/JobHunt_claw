package com.git.hui.jobclaw.util.net;

import com.git.hui.jobclaw.core.utils.json.JsonUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 请求工具类
 *
 * @date 2023/04/23
 */
@Slf4j
public class HttpRequestHelper {

    private static RestClient restClient = RestClient.create();

    /**
     * readData
     *
     * @param request request
     * @return result
     */
    // CHECKSTYLE:OFF:InnerAssignment
    public static String readReqData(HttpServletRequest request) {
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(request.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder stringBuilder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                stringBuilder.append(line);
            }
            return stringBuilder.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    log.error("请求参数解析异常! {}", request.getRequestURI(), e);
                }
            }
        }
    }

    public enum ReqMethod {
        GET,
        POST_FORM,
        POST_JSON,
        ;
    }

    /**
     * get 请求，参数通过params传递，要求url中有参数占位符，这样发起请求时，会自动将 params 中的参数拼接到url中
     *
     * @param url
     * @param params
     * @param res
     * @param <R>
     * @return
     */
    public static <R> R get(String url, Map<String, String> params, Class<R> res) {
        return executeHttpRequest(restClient, url, ReqMethod.GET, params, new HttpHeaders(), res);
    }

    public static <R> R get(String url, Class<R> res) {
        return executeHttpRequest(restClient, url, ReqMethod.GET, new HashMap<>(), new HttpHeaders(), res);
    }

    public static <R> R postJsonData(String url, Object data, Class<R> res) {
        return executeHttpRequest(restClient, url, ReqMethod.POST_JSON, data, new HttpHeaders(), res);
    }

    private static <R> R executeHttpRequest(RestClient restClient, String url, ReqMethod req, Object params, HttpHeaders headers, Class<R> responseClass) {
        try {
            SslUtils.ignoreSSL();
            return switch (req) {
                case GET -> restClient.get()
                        .uri(builder -> {
                            // 解析完整URL
                            try {
                                URL u = new URL(url);

                                // 设置URI组件
                                builder.scheme(u.getProtocol())
                                        .host(u.getHost())
                                        .port(u.getPort());
                                // 设置路径
                                String path = u.getPath();
                                if (path != null && !path.isEmpty()) {
                                    builder.path(path);
                                }
                            } catch (MalformedURLException e) {
                                throw new RuntimeException(e);
                            }

                            // 处理查询参数
                            if (params instanceof Map) {
                                Map<String, String> queryParams = (Map<String, String>) params;
                                queryParams.forEach(builder::queryParam);
                            }
                            return builder.build();
                        })
                        .headers(httpHeaders -> httpHeaders.addAll(headers))
                        .retrieve()
                        .body(responseClass);
                case POST_FORM -> {
                    MultiValueMap<String, String> args = new LinkedMultiValueMap<>();
                    args.setAll((Map<String, String>) params);
                    yield restClient.post()
                            .uri(url)
                            .headers(httpHeaders -> {
                                httpHeaders.addAll(headers);
                                if (!httpHeaders.containsHeader("Content-Type")) {
                                    httpHeaders.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
                                }
                            })
                            .body(args)
                            .retrieve()
                            .body(responseClass);
                }
                case POST_JSON ->{
                    String body = JsonUtil.toStr(params);
                    yield restClient.post()
                            .uri(url)
                            .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                            // 微信会检查Content-Length参数，但Spring Framework 6.1以后请求不再设置该参数
                            .header("Content-Length", body.getBytes(StandardCharsets.UTF_8).length + "")
                            .body(params)
                            .retrieve()
                            .body(responseClass);
                }
            };
        } catch (Exception e) {
            log.warn("Failed to fetch content, url:{}, params:{}, exception:{}", url, params, e.getMessage());
            return null;
        }
    }

//
//    public static <R> R postJsonData(String url, Object data, Class<R> res) {
//        ResponseEntity<R> responseEntity;
//        try {
//            RestTemplate restTemplate = new RestTemplate();
//            String body = JsonUtil.toStr(data);
//
//            // 设置请求头为 application/json
//            HttpHeaders headers = new HttpHeaders();
//            headers.setContentType(MediaType.APPLICATION_JSON);
//            headers.setContentLength(body.getBytes(StandardCharsets.UTF_8).length);
//
//            HttpEntity<Object> entity = new HttpEntity<>(body, headers);
//            responseEntity = restTemplate.exchange(url, HttpMethod.POST, entity, res);
//        } catch (Exception e) {
//            log.warn("Failed to fetch content, url:{}, params:{}, exception:{}", url, data, e.getMessage());
//            return null;
//        }
//
//        return responseEntity.getBody();
//    }
}