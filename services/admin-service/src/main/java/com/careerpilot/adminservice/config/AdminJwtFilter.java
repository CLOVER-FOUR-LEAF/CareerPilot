package com.careerpilot.adminservice.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Enumeration;

/**
 * 管理端鉴权：自己校验 JWT，而不是信任网关注入的 {@code X-User-Role}。
 *
 * <p>为什么不能只靠网关：
 * <ul>
 *   <li>服务端口直接暴露在内网时，网关那层就被绕过去了；</li>
 *   <li>{@code X-User-Role} 只是一个请求头，任何能直连 8086 的进程都能伪造。</li>
 * </ul>
 * 所以这里用与用户中心相同的密钥验签，并只认签名里的 {@code role}。
 * 验签通过后，把 {@code X-User-Id} 重写成令牌里的 subject，
 * 下游 Controller 继续读这个头即可，不需要改签名。
 */
@Slf4j
@Component
public class AdminJwtFilter extends OncePerRequestFilter {

    private static final String ADMIN_PREFIX = "/api/v1/admin/";
    private static final String ROLE_ADMIN = "ADMIN";
    private static final String USER_ID_HEADER = "X-User-Id";

    @Value("${jwt.secret:careerpilot-default-secret-key-must-be-at-least-256-bits-long-for-hs256}")
    private String jwtSecret;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI();
        if (!path.startsWith(ADMIN_PREFIX) || "OPTIONS".equalsIgnoreCase(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED, 1401, "未登录");
            return;
        }

        String userId;
        try {
            SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(authHeader.substring(7))
                    .getPayload();

            String role = claims.get("role", String.class);
            userId = claims.getSubject();
            if (!ROLE_ADMIN.equals(role)) {
                writeError(response, HttpServletResponse.SC_FORBIDDEN, 1403, "需要平台管理员权限");
                return;
            }
            if (userId == null || userId.isBlank()) {
                writeError(response, HttpServletResponse.SC_UNAUTHORIZED, 1401, "令牌缺少用户身份");
                return;
            }
        } catch (Exception e) {
            log.debug("管理端 JWT 校验失败: {}", e.getMessage());
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED, 1401, "登录已失效，请重新登录");
            return;
        }

        // 只把「验签失败」映射成 401。业务异常必须继续向上抛，
        // 否则任何一个 SQL 错误都会被误报成「登录已失效」，极难排查。
        chain.doFilter(new AdminRequestWrapper(request, userId), response);
    }

    private void writeError(HttpServletResponse response, int httpStatus, int code, String message)
            throws IOException {
        response.setStatus(httpStatus);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(
                "{\"code\":" + code + ",\"message\":\"" + message + "\",\"data\":null,\"traceId\":\"\"}");
    }

    /**
     * 把 {@code X-User-Id} 固定成验签后的 subject，客户端传什么都不再生效。
     */
    private static class AdminRequestWrapper extends HttpServletRequestWrapper {

        private final String userId;

        AdminRequestWrapper(HttpServletRequest request, String userId) {
            super(request);
            this.userId = userId;
        }

        @Override
        public String getHeader(String name) {
            if (USER_ID_HEADER.equalsIgnoreCase(name)) {
                return userId;
            }
            return super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            if (USER_ID_HEADER.equalsIgnoreCase(name)) {
                return Collections.enumeration(Collections.singletonList(userId));
            }
            return super.getHeaders(name);
        }
    }
}
