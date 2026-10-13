package com.git.hui.jobclaw.openapi.client;

import com.git.hui.jobclaw.core.bizexception.StatusEnum;
import com.git.hui.jobclaw.core.utils.json.JsonUtil;
import com.git.hui.jobclaw.openapi.model.OpenApiUserDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 可选的外部账号静默登录客户端。
 * 外部登录默认关闭，只有配置 endpoint 后才会发起请求。
 */
@Component
public class ExternalSsoLoginClient {
    private final RestTemplate restTemplate;
    private final String endpoint;
    private final String appId;

    public ExternalSsoLoginClient(
            @Value("${jobclaw.external-sso.endpoint:}") String endpoint,
            @Value("${jobclaw.external-sso.app-id:jobclaw}") String appId) {
        this.endpoint = endpoint;
        this.appId = appId;
        this.restTemplate = new RestTemplate();
        ClientHttpRequestInterceptor interceptor = (request, body, execution) -> {
            request.getHeaders().add("jobclaw-sso-app-id", this.appId);
            return execution.execute(request, body);
        };
        restTemplate.setInterceptors(Collections.singletonList(interceptor));
    }

    public OpenApiUserDTO loginBySession(String session) {
        if (!StringUtils.hasText(endpoint) || !StringUtils.hasText(session)) {
            return null;
        }
        String url = UriComponentsBuilder.fromUriString(endpoint)
                .queryParam("token", session)
                .toUriString();
        String content = restTemplate.getForObject(url, String.class);
        if (!StringUtils.hasText(content)) {
            return null;
        }
        Map map = JsonUtil.toObj(content, HashMap.class);
        Map status = (Map) map.get("status");
        if (status != null && status.get("code").equals(StatusEnum.SUCCESS.getCode())) {
            Object res = map.get("result");
            return JsonUtil.toObj(JsonUtil.toStr(res), OpenApiUserDTO.class);
        }
        return null;
    }
}
