package io.github.ajax1.campusradar.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/**
 * 访问控制：
 * <ul>
 *     <li>/api/admin/**：只允许直连的本机请求。经 cloudflared、nginx 等转发进来的请求在服务端看来也是 127.0.0.1，
 *     所以带转发头的一律拒绝</li>
 *     <li>/mcp、/api/**：配置了令牌时必须带 Authorization: Bearer &lt;token&gt;</li>
 * </ul>
 */
public class AccessFilter extends OncePerRequestFilter {

    static final List<String> FORWARD_HEADERS = List.of(
            "X-Forwarded-For", "X-Forwarded-Host", "X-Real-IP", "Forwarded", "CF-Connecting-IP", "CF-Ray");

    private final String token;

    public AccessFilter(String token) {
        this.token = token == null || token.isBlank() ? null : token;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        if (path.startsWith("/api/admin")) {
            if (!isDirectLocal(request)) {
                reject(response, HttpServletResponse.SC_FORBIDDEN, "admin endpoints are only available to direct local requests");
                return;
            }
        } else if (token != null && (path.equals("/mcp") || path.startsWith("/mcp/") || path.startsWith("/api/"))
                && !matches(request.getHeader("Authorization"), token)) {
            reject(response, HttpServletResponse.SC_UNAUTHORIZED, "missing or invalid bearer token");
            return;
        }
        chain.doFilter(request, response);
    }

    public static boolean isDirectLocal(HttpServletRequest request) {
        for (String h : FORWARD_HEADERS) {
            if (request.getHeader(h) != null) {
                return false;
            }
        }
        try {
            return InetAddress.getByName(request.getRemoteAddr()).isLoopbackAddress();
        } catch (UnknownHostException e) {
            return false;
        }
    }

    static boolean matches(String header, String token) {
        if (header == null || !header.startsWith("Bearer ")) {
            return false;
        }
        byte[] given = header.substring(7).trim().getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(given, token.getBytes(StandardCharsets.UTF_8));
    }

    private static void reject(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"" + message + "\"}");
    }
}
