package com.careerpilot.adminservice.controller;

import com.careerpilot.adminservice.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 运营概览。
 *
 * <p>统计口径尽量用「数据库直接聚合」而不是调各服务接口：
 * 管理端页面刷新频率低，少一跳依赖就少一种「某个服务挂了概览打不开」的故障。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/stats")
@RequiredArgsConstructor
public class AdminStatsController {

    private final JdbcTemplate jdbc;

    @GetMapping("/overview")
    public ApiResponse<Map<String, Object>> overview() {
        Map<String, Object> data = new LinkedHashMap<>();

        data.put("totalUsers", count("SELECT COUNT(*) FROM t_user"));
        data.put("recruiterCount", count("SELECT COUNT(*) FROM t_user WHERE role = 'RECRUITER'"));
        data.put("adminCount", count("SELECT COUNT(*) FROM t_user WHERE role = 'ADMIN'"));
        data.put("todayNewUsers", count("SELECT COUNT(*) FROM t_user WHERE DATE(created_at) = CURDATE()"));

        data.put("totalResumes", count("SELECT COUNT(*) FROM t_resume"));
        data.put("totalJds", count("SELECT COUNT(*) FROM t_jd"));

        data.put("totalAiTasks", count("SELECT COUNT(*) FROM t_ai_task"));
        data.put("runningAiTasks", count("SELECT COUNT(*) FROM t_ai_task WHERE status IN ('PENDING','RUNNING')"));
        data.put("failedAiTasks", count("SELECT COUNT(*) FROM t_ai_task WHERE status = 'FAILED'"));
        data.put("todayAiTasks", count("SELECT COUNT(*) FROM t_ai_task WHERE DATE(created_at) = CURDATE()"));

        data.put("totalInsights", count("SELECT COUNT(*) FROM t_hr_insight"));
        data.put("paidOrders", count("SELECT COUNT(*) FROM t_order WHERE status = 1"));
        data.put("revenueCents", count("SELECT COALESCE(SUM(amount_cents), 0) FROM t_order WHERE status = 1"));

        return ApiResponse.success(data);
    }

    /**
     * 近 14 天趋势 + 任务类型 / 角色 / 状态分布。
     *
     * <p>和概览一样直接读业务库：管理端低频访问，少一跳依赖就少一种
     * 「某个服务挂了趋势图打不开」的故障。任何一张表缺失时对应序列返回 0。
     */
    @GetMapping("/trends")
    public ApiResponse<Map<String, Object>> trends() {
        Map<String, Object> data = new LinkedHashMap<>();

        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(13);
        DateTimeFormatter fmt = DateTimeFormatter.ISO_LOCAL_DATE;

        Map<String, Long> users = dailyCount(
                "SELECT DATE(created_at) AS d, COUNT(*) AS c FROM t_user "
                        + "WHERE created_at >= ? GROUP BY DATE(created_at)", start);
        Map<String, Long> tasks = dailyCount(
                "SELECT DATE(created_at) AS d, COUNT(*) AS c FROM t_ai_task "
                        + "WHERE created_at >= ? GROUP BY DATE(created_at)", start);
        Map<String, Long> resumes = dailyCount(
                "SELECT DATE(created_at) AS d, COUNT(*) AS c FROM t_resume "
                        + "WHERE created_at >= ? AND is_deleted = 0 GROUP BY DATE(created_at)", start);
        Map<String, Long> jds = dailyCount(
                "SELECT DATE(created_at) AS d, COUNT(*) AS c FROM t_jd "
                        + "WHERE created_at >= ? AND is_deleted = 0 GROUP BY DATE(created_at)", start);
        Map<String, Long> matches = dailyCount(
                "SELECT DATE(created_at) AS d, COUNT(*) AS c FROM t_match_record "
                        + "WHERE created_at >= ? GROUP BY DATE(created_at)", start);

        List<Map<String, Object>> daily = new ArrayList<>();
        for (LocalDate day = start; !day.isAfter(end); day = day.plusDays(1)) {
            String key = day.format(fmt);
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("date", key);
            point.put("newUsers", users.getOrDefault(key, 0L));
            point.put("aiTasks", tasks.getOrDefault(key, 0L));
            point.put("resumes", resumes.getOrDefault(key, 0L));
            point.put("jds", jds.getOrDefault(key, 0L));
            point.put("matches", matches.getOrDefault(key, 0L));
            daily.add(point);
        }
        data.put("daily", daily);

        data.put("taskTypes", groupCount(
                "SELECT task_type AS name, COUNT(*) AS count FROM t_ai_task "
                        + "GROUP BY task_type ORDER BY count DESC"));
        data.put("roles", groupCount(
                "SELECT role AS name, COUNT(*) AS count FROM t_user "
                        + "GROUP BY role ORDER BY count DESC"));

        List<Map<String, Object>> statusRows = groupCount(
                "SELECT status AS name, COUNT(*) AS count FROM t_ai_task GROUP BY status");
        long totalTasks = statusRows.stream()
                .mapToLong(r -> ((Number) r.get("count")).longValue()).sum();
        long successTasks = statusRows.stream()
                .filter(r -> "SUCCESS".equals(String.valueOf(r.get("name"))))
                .mapToLong(r -> ((Number) r.get("count")).longValue()).sum();
        data.put("taskStatus", statusRows);
        data.put("successRate", totalTasks == 0 ? null
                : BigDecimal.valueOf(successTasks)
                        .divide(BigDecimal.valueOf(totalTasks), 3, RoundingMode.HALF_UP)
                        .doubleValue());

        return ApiResponse.success(data);
    }

    private Map<String, Long> dailyCount(String sql, LocalDate start) {
        Map<String, Long> result = new LinkedHashMap<>();
        try {
            for (Map<String, Object> row : jdbc.queryForList(sql, start)) {
                Object day = row.get("d");
                Object count = row.get("c");
                if (day == null || count == null) {
                    continue;
                }
                result.put(String.valueOf(day),
                        count instanceof Number n ? n.longValue() : Long.parseLong(String.valueOf(count)));
            }
        } catch (Exception e) {
            log.warn("趋势查询失败，按空序列处理: sql={}, err={}", sql, e.getMessage());
        }
        return result;
    }

    private List<Map<String, Object>> groupCount(String sql) {
        List<Map<String, Object>> result = new ArrayList<>();
        try {
            for (Map<String, Object> row : jdbc.queryForList(sql)) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("name", row.get("name"));
                Object count = row.get("count");
                item.put("count", count instanceof Number n ? n.longValue() : 0L);
                result.add(item);
            }
        } catch (Exception e) {
            log.warn("分组统计失败，按空序列处理: sql={}, err={}", sql, e.getMessage());
        }
        return result;
    }

    /**
     * 单条统计失败不应该让整个概览 500。
     * 表还没建（例如新环境只跑了 V1）时返回 0，运营页仍然可用。
     */
    private long count(String sql) {
        try {
            Long value = jdbc.queryForObject(sql, Long.class);
            return value == null ? 0L : value;
        } catch (Exception e) {
            log.warn("统计查询失败，按 0 处理: sql={}, err={}", sql, e.getMessage());
            return 0L;
        }
    }
}
