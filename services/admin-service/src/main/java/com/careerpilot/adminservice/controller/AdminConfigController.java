package com.careerpilot.adminservice.controller;

import com.careerpilot.adminservice.dto.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 平台配置。
 *
 * <p>配置以 key -> JSON 存放，新增开关不需要改表。
 * 当前只做「运营方配置」；租户级配置等 B 端协作需求明确后再加。
 *
 * <p><b>不要在这里存明文密钥。</b> 模型 API Key 走部署环境 secret，
 * 这里只存「用哪个模型、开不开、温度多少」。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/configs")
@RequiredArgsConstructor
public class AdminConfigController {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list() {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT config_key AS configKey, config_value AS configValue,
                       description, updated_by AS updatedBy, updated_at AS updatedAt
                FROM t_platform_config ORDER BY config_key
                """);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = new LinkedHashMap<>(row);
            item.put("value", parseJson(row.get("configValue")));
            item.remove("configValue");
            result.add(item);
        }
        return ApiResponse.success(result);
    }

    @PutMapping("/{key}")
    public ApiResponse<Void> update(@PathVariable String key,
                                    @RequestBody Map<String, Object> body,
                                    @RequestHeader("X-User-Id") String actorId) {
        if (key == null || !key.matches("[A-Za-z0-9._-]{1,64}")) {
            return ApiResponse.error(1400, "非法配置键");
        }
        if (!body.containsKey("value")) {
            return ApiResponse.error(1400, "缺少 value");
        }

        String valueJson;
        try {
            valueJson = objectMapper.writeValueAsString(body.get("value"));
        } catch (Exception e) {
            return ApiResponse.error(1400, "value 不是合法 JSON");
        }

        jdbc.update("""
                INSERT INTO t_platform_config (config_key, config_value, updated_by)
                VALUES (?, ?, ?)
                ON DUPLICATE KEY UPDATE config_value = VALUES(config_value), updated_by = VALUES(updated_by)
                """, key, valueJson, actorId);

        try {
            jdbc.update("""
                    INSERT INTO t_admin_audit_log
                        (audit_id, actor_id, action, target_type, target_id, detail_json)
                    VALUES (?, ?, 'config.update', 'config', ?, ?)
                    """, "aud_" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 16),
                    actorId, key, valueJson);
        } catch (Exception e) {
            log.warn("配置变更审计写入失败: key={}, err={}", key, e.getMessage());
        }
        return ApiResponse.success();
    }

    private Object parseJson(Object raw) {
        if (raw == null) {
            return null;
        }
        try {
            return objectMapper.readValue(String.valueOf(raw), Object.class);
        } catch (Exception e) {
            return raw;
        }
    }
}
