package com.careerpilot.adminservice.controller;

import com.careerpilot.adminservice.dto.ApiResponse;
import com.careerpilot.adminservice.dto.PageResult;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 用户与角色管理。
 *
 * <p>角色只有三个值：USER / RECRUITER / ADMIN。
 * RECRUITER 是「授权」，不是「账号类型」—— 升级后原账号的求职数据仍然在。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private static final List<String> ROLES = List.of("USER", "RECRUITER", "ADMIN");

    private final JdbcTemplate jdbc;

    @GetMapping
    public ApiResponse<PageResult<Map<String, Object>>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String role) {

        int safePage = Math.max(1, page);
        int safeSize = Math.min(100, Math.max(1, size));

        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        List<Object> args = new ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND (u.email LIKE ? OR u.nickname LIKE ? OR u.user_id LIKE ?)");
            String like = "%" + keyword.trim() + "%";
            args.add(like);
            args.add(like);
            args.add(like);
        }
        if (role != null && !role.isBlank()) {
            where.append(" AND u.role = ?");
            args.add(role);
        }

        long total = jdbc.queryForObject("SELECT COUNT(*) FROM t_user u" + where, Long.class,
                args.toArray()).longValue();

        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(safeSize);
        pageArgs.add((safePage - 1) * safeSize);

        List<Map<String, Object>> records = jdbc.queryForList("""
                SELECT u.id                 AS id,
                       u.user_id            AS userId,
                       u.email              AS email,
                       u.nickname           AS nickname,
                       u.role               AS role,
                       u.status             AS status,
                       u.last_login_at      AS lastLoginAt,
                       u.created_at         AS createdAt,
                       m.level              AS membershipLevel,
                       m.ai_quota_remaining AS aiQuotaRemaining
                FROM t_user u
                LEFT JOIN t_membership m ON m.user_id = u.user_id
                """ + where + " ORDER BY u.id DESC LIMIT ? OFFSET ?", pageArgs.toArray());

        return ApiResponse.success(PageResult.of(records, total, safePage, safeSize));
    }

    @PutMapping("/{id}/role")
    public ApiResponse<Void> updateRole(@PathVariable Long id,
                                        @RequestBody RoleRequest body,
                                        @RequestHeader("X-User-Id") String actorId) {
        String role = body.getRole() == null ? "" : body.getRole().trim().toUpperCase();
        if (!ROLES.contains(role)) {
            return ApiResponse.error(1400, "非法角色: " + body.getRole());
        }

        Map<String, Object> user = findById(id);
        if (user == null) {
            return ApiResponse.error(1404, "用户不存在");
        }

        // 防止把最后一个管理员降级，导致管理端永久锁死
        if ("ADMIN".equals(user.get("role")) && !"ADMIN".equals(role)) {
            Long admins = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM t_user WHERE role = 'ADMIN'", Long.class);
            if (admins == null || admins <= 1) {
                return ApiResponse.error(1409, "至少保留一名管理员");
            }
        }

        jdbc.update("UPDATE t_user SET role = ? WHERE id = ?", role, id);
        audit(actorId, "user.role.update", "user", String.valueOf(id),
                "{\"from\":\"" + user.get("role") + "\",\"to\":\"" + role + "\"}");
        return ApiResponse.success();
    }

    @PutMapping("/{id}/status")
    public ApiResponse<Void> updateStatus(@PathVariable Long id,
                                          @RequestBody StatusRequest body,
                                          @RequestHeader("X-User-Id") String actorId) {
        if (body.getStatus() == null || (body.getStatus() != 0 && body.getStatus() != 1)) {
            return ApiResponse.error(1400, "status 只能是 0 或 1");
        }
        Map<String, Object> user = findById(id);
        if (user == null) {
            return ApiResponse.error(1404, "用户不存在");
        }
        jdbc.update("UPDATE t_user SET status = ? WHERE id = ?", body.getStatus(), id);
        audit(actorId, "user.status.update", "user", String.valueOf(id),
                "{\"status\":" + body.getStatus() + "}");
        return ApiResponse.success();
    }

    @GetMapping("/audits")
    public ApiResponse<PageResult<Map<String, Object>>> audits(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(100, Math.max(1, size));
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM t_admin_audit_log", Long.class).longValue();
        List<Map<String, Object>> records = jdbc.queryForList("""
                SELECT audit_id AS auditId, actor_id AS actorId, action, target_type AS targetType,
                       target_id AS targetId, detail_json AS detail, ip, created_at AS createdAt
                FROM t_admin_audit_log
                ORDER BY id DESC LIMIT ? OFFSET ?
                """, safeSize, (safePage - 1) * safeSize);
        return ApiResponse.success(PageResult.of(records, total, safePage, safeSize));
    }

    private Map<String, Object> findById(Long id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, user_id AS userId, role FROM t_user WHERE id = ?", id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private void audit(String actorId, String action, String targetType, String targetId, String detail) {
        try {
            jdbc.update("""
                    INSERT INTO t_admin_audit_log
                        (audit_id, actor_id, action, target_type, target_id, detail_json)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """, "aud_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16),
                    actorId, action, targetType, targetId, detail);
        } catch (Exception e) {
            // 审计写失败不能阻断业务操作，但必须留日志
            log.warn("审计日志写入失败: action={}, target={}, err={}", action, targetId, e.getMessage());
        }
    }

    @Data
    public static class RoleRequest {
        private String role;
    }

    @Data
    public static class StatusRequest {
        private Integer status;
    }
}
