package com.careerpilot.adminservice.controller;

import com.careerpilot.adminservice.config.ProbeProperties;
import com.careerpilot.adminservice.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 服务健康总览。
 *
 * <p>并发探测而不是串行：14 个进程串行、每个 1.5s 超时，最坏要 20 秒，
 * 运营页会以为后台卡死。这里整体超时 3 秒。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/services")
@RequiredArgsConstructor
public class AdminServiceHealthController {

    private final ProbeProperties probeProperties;

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(1))
            .build();

    @GetMapping("/health")
    public ApiResponse<List<Map<String, Object>>> health() {
        List<CompletableFuture<Map<String, Object>>> futures = new ArrayList<>();

        for (ProbeProperties.Probe probe : probeProperties.getProbes()) {
            futures.add(probe(probe));
        }

        List<Map<String, Object>> result = new ArrayList<>();
        List<ProbeProperties.Probe> probes = probeProperties.getProbes();
        for (int i = 0; i < futures.size(); i++) {
            ProbeProperties.Probe probe = probes.get(i);
            try {
                result.add(futures.get(i).get(3, TimeUnit.SECONDS));
            } catch (Exception e) {
                Map<String, Object> down = new LinkedHashMap<>();
                down.put("name", probe.getName());
                down.put("group", probe.getGroup());
                down.put("url", probe.getUrl());
                down.put("up", false);
                down.put("status", -1);
                down.put("latencyMs", -1);
                down.put("error", "探测超时");
                result.add(down);
            }
        }
        return ApiResponse.success(result);
    }

    private CompletableFuture<Map<String, Object>> probe(ProbeProperties.Probe probe) {
        long start = System.currentTimeMillis();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(probe.getUrl()))
                .timeout(Duration.ofSeconds(2))
                .GET()
                .build();

        return CLIENT.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                .handle((response, error) -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("name", probe.getName());
                    item.put("group", probe.getGroup());
                    item.put("url", probe.getUrl());
                    item.put("latencyMs", System.currentTimeMillis() - start);
                    if (error != null) {
                        item.put("up", false);
                        item.put("status", -1);
                        item.put("error", error.getClass().getSimpleName());
                    } else {
                        item.put("up", response.statusCode() >= 200 && response.statusCode() < 300);
                        item.put("status", response.statusCode());
                    }
                    return item;
                });
    }
}
