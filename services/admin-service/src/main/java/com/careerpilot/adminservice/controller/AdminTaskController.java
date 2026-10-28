package com.careerpilot.adminservice.controller;

import com.careerpilot.adminservice.dto.ApiResponse;
import com.careerpilot.adminservice.dto.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * AI 任务总览 —— 运营排查「某个功能为什么没结果」的第一入口。
 */
@RestController
@RequestMapping("/api/v1/admin/tasks")
@RequiredArgsConstructor
public class AdminTaskController {

    private final JdbcTemplate jdbc;

    @GetMapping
    public ApiResponse<PageResult<Map<String, Object>>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String taskType) {

        int safePage = Math.max(1, page);
        int safeSize = Math.min(100, Math.max(1, size));

        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        List<Object> args = new ArrayList<>();
        if (status != null && !status.isBlank()) {
            where.append(" AND status = ?");
            args.add(status);
        }
        if (taskType != null && !taskType.isBlank()) {
            where.append(" AND task_type = ?");
            args.add(taskType);
        }

        long total = jdbc.queryForObject("SELECT COUNT(*) FROM t_ai_task" + where, Long.class,
                args.toArray()).longValue();

        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(safeSize);
        pageArgs.add((safePage - 1) * safeSize);

        List<Map<String, Object>> records = jdbc.queryForList("""
                SELECT task_id AS taskId, task_type AS taskType, status, progress,
                       current_agent AS currentAgent, user_id AS userId,
                       duration_ms AS durationMs, error_msg AS errorMessage,
                       created_at AS createdAt, completed_at AS completedAt
                FROM t_ai_task
                """ + where + " ORDER BY id DESC LIMIT ? OFFSET ?", pageArgs.toArray());

        return ApiResponse.success(PageResult.of(records, total, safePage, safeSize));
    }
}
