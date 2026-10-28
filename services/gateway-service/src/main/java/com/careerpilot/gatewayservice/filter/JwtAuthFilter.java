package com.careerpilot.gatewayservice.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 网关鉴权：校验 JWT，把用户身份写进 {@code X-User-Id} 交给下游。
 *
 * <p>下游服务一律只信任这个头，不看也不解析 Authorization ——
 * 它们本来就拿不到能独立验证签名的理由。
 */
@Slf4j
@Component
public class JwtAuthFilter implements GlobalFilter, Ordered {

    @Value("${jwt.secret:careerpilot-default-secret-key-must-be-at-least-256-bits-long-for-hs256}")
    private String jwtSecret;

    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String USER_ROLE_HEADER = "X-User-Role";

    private static final String ROLE_USER = "USER";
    private static final String ROLE_RECRUITER = "RECRUITER";
    private static final String ROLE_ADMIN = "ADMIN";

    /** 无需登录即可访问的路径。 */
    private static final List<String> WHITE_LIST = List.of(
            "/api/v1/auth/login",
            "/api/v1/auth/register",
            "/api/v1/auth/refresh"
    );

    /** 网关自身提供的公开端点。 */
    private static final List<String> PUBLIC_PATHS = List.of(
            "/actuator/health"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        // 服务间回调接口（jd/resume/match 的 /internal/**）绝不能从公网进来
        if (path.startsWith("/internal/")) {
            return forbidden(exchange, "内部接口不允许通过网关访问");
        }

        if (PUBLIC_PATHS.stream().anyMatch(path::equals)) {
            return chain.filter(exchange);
        }

        if (WHITE_LIST.stream().anyMatch(path::startsWith)) {
            // 白名单路径也要剥掉伪造的身份头，防止把别人的身份"带"进去
            return chain.filter(exchange.mutate()
                    .request(stripIdentityHeaders(request)).build());
        }

        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return unauthorized(exchange);
        }

        String userId;
        String role;
        try {
            SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(authHeader.substring(7))
                    .getPayload();
            userId = claims.getSubject();
            role = normalizeRole(claims.get("role", String.class));
        } catch (Exception e) {
            log.debug("JWT 校验失败: {}", e.getMessage());
            return unauthorized(exchange);
        }

        if (userId == null || userId.isBlank()) {
            return unauthorized(exchange);
        }

        // 路由级授权：管理端 / HR 端按角色收口。
        // 放在网关而不是各业务服务里，是为了「新增一个服务时不会漏配权限」。
        String denied = authorize(path, role);
        if (denied != null) {
            return forbidden(exchange, denied);
        }

        /*
         * 必须先剥掉客户端传来的 X-User-Id / X-User-Role 再写真正的值。
         *
         * mutate().header() 是「追加」而不是「替换」—— 只追加的话，
         * 外部请求自带一个 X-User-Id 就能让下游读到伪造的身份，直接变成越权。
         */
        ServerHttpRequest mutated = stripIdentityHeaders(request)
                .mutate()
                .header(USER_ID_HEADER, userId)
                .header(USER_ROLE_HEADER, role)
                .build();

        return chain.filter(exchange.mutate().request(mutated).build());
    }

    /**
     * 返回 null 表示放行，否则返回给前端的拒绝原因。
     *
     * <p>注意这里只做「粗粒度入口授权」：某个 HR 能不能看某份简历，
     * 仍然由业务服务基于数据归属做二次校验，两层缺一不可。
     */
    private String authorize(String path, String role) {
        if (path.startsWith("/api/v1/admin/")) {
            return ROLE_ADMIN.equals(role) ? null : "需要平台管理员权限";
        }
        if (path.startsWith("/api/v1/hr/")) {
            return (ROLE_RECRUITER.equals(role) || ROLE_ADMIN.equals(role))
                    ? null : "需要招聘方权限";
        }
        return null;
    }

    private String normalizeRole(String role) {
        if (ROLE_RECRUITER.equals(role) || ROLE_ADMIN.equals(role)) {
            return role;
        }
        return ROLE_USER;
    }

    private ServerHttpRequest stripIdentityHeaders(ServerHttpRequest request) {
        return request.mutate()
                .headers(headers -> {
                    headers.remove(USER_ID_HEADER);
                    headers.remove(USER_ROLE_HEADER);
                })
                .build();
    }

    @Override
    public int getOrder() {
        // 必须早于路由转发执行
        return -100;
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        return writeError(exchange, HttpStatus.UNAUTHORIZED, 401, "登录已失效，请重新登录");
    }

    private Mono<Void> forbidden(ServerWebExchange exchange, String message) {
        return writeError(exchange, HttpStatus.FORBIDDEN, 1403, message);
    }

    private Mono<Void> writeError(ServerWebExchange exchange, HttpStatus status, int code, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String body = """
                {"code":%d,"message":"%s","data":null,"traceId":""}
                """.formatted(code, message);

        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }
}
