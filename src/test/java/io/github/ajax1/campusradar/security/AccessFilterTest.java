package io.github.ajax1.campusradar.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class AccessFilterTest {

    @Test
    void tokenProtectsMcpAndApi() throws Exception {
        AccessFilter filter = new AccessFilter("secret");
        assertThat(status(filter, request("/mcp", "127.0.0.1"))).isEqualTo(401);
        assertThat(status(filter, request("/api/jobs", "127.0.0.1"))).isEqualTo(401);

        MockHttpServletRequest withToken = request("/api/jobs", "203.0.113.5");
        withToken.addHeader("Authorization", "Bearer secret");
        assertThat(status(filter, withToken)).isEqualTo(200);

        MockHttpServletRequest wrongToken = request("/mcp", "203.0.113.5");
        wrongToken.addHeader("Authorization", "Bearer nope");
        assertThat(status(filter, wrongToken)).isEqualTo(401);
    }

    @Test
    void withoutTokenEverythingButAdminIsOpen() throws Exception {
        AccessFilter filter = new AccessFilter("");
        assertThat(status(filter, request("/mcp", "203.0.113.5"))).isEqualTo(200);
        assertThat(status(filter, request("/api/jobs", "203.0.113.5"))).isEqualTo(200);
    }

    @Test
    void adminOnlyForDirectLocalRequests() throws Exception {
        AccessFilter filter = new AccessFilter("secret");
        assertThat(status(filter, request("/api/admin/collect", "127.0.0.1"))).isEqualTo(200);
        assertThat(status(filter, request("/api/admin/collect", "203.0.113.5"))).isEqualTo(403);

        // cloudflared 转发进来的请求 remoteAddr 也是 127.0.0.1，但带 CF 头
        MockHttpServletRequest tunneled = request("/api/admin/collect", "127.0.0.1");
        tunneled.addHeader("CF-Connecting-IP", "198.51.100.7");
        assertThat(status(filter, tunneled)).isEqualTo(403);

        // 带了正确令牌也不行
        MockHttpServletRequest proxiedWithToken = request("/api/admin/collect", "127.0.0.1");
        proxiedWithToken.addHeader("X-Forwarded-For", "198.51.100.7");
        proxiedWithToken.addHeader("Authorization", "Bearer secret");
        assertThat(status(filter, proxiedWithToken)).isEqualTo(403);
    }

    private static MockHttpServletRequest request(String uri, String remoteAddr) {
        MockHttpServletRequest r = new MockHttpServletRequest("POST", uri);
        r.setRemoteAddr(remoteAddr);
        return r;
    }

    private static int status(AccessFilter filter, MockHttpServletRequest request) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response.getStatus();
    }
}
