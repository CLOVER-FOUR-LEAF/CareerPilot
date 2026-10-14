package com.careerpilot.gatewayservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import org.springframework.web.util.pattern.PathPatternParser;

import java.util.List;

/**
 * 跨域配置。
 *
 * <p>本地开发时前端跑在 3000 端口、网关在 8080，属于跨域；
 * 生产由 Nginx 同源反代，这份配置不生效但也无害。
 *
 * <p>注意：这里的 CORS 过滤器必须排在 {@code JwtAuthFilter} 之前，
 * 否则预检请求（OPTIONS，本身不带 Authorization）会先被鉴权拦掉。
 */
@Configuration
public class CorsConfig {

    @Value("${cors.allowed-origins:http://localhost:3000,http://localhost:5173}")
    private List<String> allowedOrigins;

    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of(HttpHeaders.CONTENT_TYPE, "X-Trace-Id"));
        config.setAllowCredentials(true);
        // SSE 必须关缓存，否则 Nginx / 浏览器会把流式响应缓存成一坨
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource(new PathPatternParser());
        source.registerCorsConfiguration("/**", config);
        return new CorsWebFilter(source);
    }
}
