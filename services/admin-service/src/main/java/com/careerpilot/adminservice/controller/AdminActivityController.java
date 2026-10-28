package com.careerpilot.adminservice.controller;

import com.careerpilot.adminservice.dto.ApiResponse;
import com.careerpilot.adminservice.dto.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 用户行为视图。
 *
 * <p>数据直接来自业务表（注册、简历、JD、匹配、HR 洞察、AI 任务），
 * 不另建一份「行为日志」——那样很容易出现「日志里有、业务表里没有」的假记录。
 *
 * <p>这里刻意不返回简历正文、JD 正文或任何候选人个人信息，
 * 运营只需要知道「谁在什么时候用了什么能力」。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/activity")
@RequiredArgsConstructor
public class AdminActivityController {

    private final JdbcTemplate jdbc;

    /**
     * 用户使用概览：每个账号的产出数量与最近活跃时间。
     */
    @GetMapping("/users")
    public ApiResponse<PageResult<Map<String, Object>>> users(
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

        long total = jdbc.queryForObject("SELECT COUNT(*) FROM t_user u" + where,
                Long.class, args.toArray()).longValue();

        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(safeSize);
        pageArgs.add((safePage - 1) * safeSize);

        List<Map<String, Object>> records = jdbc.queryForList("""
                SELECT u.id                    AS id,
                       u.user_id               AS userId,
                       u.email                 AS email,
                       u.nickname              AS nickname,
                       u.role                  AS role,
                       u.status                AS status,
                       u.last_login_at         AS lastLoginAt,
                       u.created_at            AS createdAt,
                       (SELECT COUNT(*) FROM t_resume r
                         WHERE r.user_id = u.user_id AND r.is_deleted = 0) AS resumeCount,
                       (SELECT COUNT(*) FROM t_jd j
                         WHERE j.user_id = u.user_id AND j.is_deleted = 0) AS jdCount,
                       (SELECT COUNT(*) FROM t_match_record m
                         WHERE m.user_id = u.user_id) AS matchCount,
                       (SELECT COUNT(*) FROM t_ai_task t
                         WHERE t.user_id = u.user_id) AS aiTaskCount,
                       (SELECT COUNT(*) FROM t_hr_insight h
                         WHERE h.user_id = u.user_id) AS insightCount,
                       (SELECT MAX(t.created_at) FROM t_ai_task t
                         WHERE t.user_id = u.user_id) AS lastActiveAt
                FROM t_user u
                """ + where + """
                 ORDER BY COALESCE(
                     (SELECT MAX(t.created_at) FROM t_ai_task t WHERE t.user_id = u.user_id),
                     u.last_login_at, u.created_at) DESC, u.id DESC
                 LIMIT ? OFFSET ?
                """, pageArgs.toArray());

        return ApiResponse.success(PageResult.of(records, total, safePage, safeSize));
    }

    /**
     * 行为流水：注册 / 最近登录 / 简历上传 / JD 创建 / 匹配 / 洞察 / 生成类 AI 任务。
     *
     * <p>简历解析、JD 解析、匹配打分这几类任务分别由上传 / 创建 / 匹配事件代表，
     * 因此在 AI 任务分支里排除，避免一次操作出现两行。
     */
    @GetMapping("/events")
    public ApiResponse<PageResult<Map<String, Object>>> events(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String keyword) {

        int safePage = Math.max(1, page);
        int safeSize = Math.min(100, Math.max(1, size));

        String union = """
                SELECT 'user.register' AS action, u.user_id AS userId, u.email AS email,
                       CONCAT('注册账号：', u.email) AS summary, u.created_at AS createdAt
                FROM t_user u
                UNION ALL
                SELECT 'user.login', u.user_id, u.email,
                       CONCAT('最近登录：', u.email), u.last_login_at
                FROM t_user u WHERE u.last_login_at IS NOT NULL
                UNION ALL
                SELECT 'resume.upload', r.user_id, u.email,
                       CONCAT('上传候选人简历/简历：', COALESCE(r.title, '未命名')), r.created_at
                FROM t_resume r LEFT JOIN t_user u ON u.user_id = r.user_id
                WHERE r.is_deleted = 0
                UNION ALL
                SELECT 'jd.create', j.user_id, u.email,
                       CONCAT('创建岗位 JD：', COALESCE(j.position_name, '未命名')), j.created_at
                FROM t_jd j LEFT JOIN t_user u ON u.user_id = j.user_id
                WHERE j.is_deleted = 0
                UNION ALL
                SELECT 'match.evaluate', m.user_id, u.email,
                       CONCAT('发起岗位匹配：', m.record_id), m.created_at
                FROM t_match_record m LEFT JOIN t_user u ON u.user_id = m.user_id
                UNION ALL
                SELECT 'hr.insight', h.user_id, u.email,
                       CONCAT('生成深度洞察报告：', h.insight_id), h.created_at
                FROM t_hr_insight h LEFT JOIN t_user u ON u.user_id = h.user_id
                UNION ALL
                SELECT CONCAT('ai.', t.task_type), t.user_id, u.email,
                       CONCAT('AI 任务：', t.task_type, ' · ', t.status), t.created_at
                FROM t_ai_task t LEFT JOIN t_user u ON u.user_id = t.user_id
                WHERE t.task_type NOT IN
                      ('resume_analyze', 'jd_parse', 'match_score', 'hr_insight')
                """;

        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        List<Object> args = new ArrayList<>();
        if (action != null && !action.isBlank()) {
            where.append(" AND e.action = ?");
            args.add(action);
        }
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND (e.email LIKE ? OR e.userId LIKE ?)");
            String like = "%" + keyword.trim() + "%";
            args.add(like);
            args.add(like);
        }

        long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM (" + union + ") e" + where,
                Long.class, args.toArray()).longValue();

        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(safeSize);
        pageArgs.add((safePage - 1) * safeSize);

        List<Map<String, Object>> records = jdbc.queryForList(
                "SELECT e.action, e.userId, e.email, e.summary, e.createdAt FROM ("
                        + union + ") e" + where
                        + " ORDER BY e.createdAt DESC, e.action LIMIT ? OFFSET ?",
                pageArgs.toArray());

        return ApiResponse.success(PageResult.of(records, total, safePage, safeSize));
    }
}
