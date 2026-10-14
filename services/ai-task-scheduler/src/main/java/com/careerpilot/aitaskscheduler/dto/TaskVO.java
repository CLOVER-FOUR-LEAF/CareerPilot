package com.careerpilot.aitaskscheduler.dto;

import cn.hutool.json.JSONUtil;
import com.careerpilot.aitaskscheduler.entity.AiTask;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 任务对外视图。
 *
 * <p>与 {@link AiTask} 实体的区别在于 JSON 列被还原成了对象 —— 契约里
 * {@code input} / {@code output} 对前端是对象，不是一坨转义后的字符串。
 */
@Data
public class TaskVO {

    private String taskId;
    private String userId;
    private String taskType;
    private String bizId;
    private String status;
    private Integer progress;
    private String currentAgent;
    private Map<String, Object> input;
    private Map<String, Object> output;
    private String errorMessage;
    private Integer retryCount;
    private Integer durationMs;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static TaskVO from(AiTask task) {
        if (task == null) {
            return null;
        }
        TaskVO vo = new TaskVO();
        vo.setTaskId(task.getTaskId());
        vo.setUserId(task.getUserId());
        vo.setTaskType(task.getTaskType());
        vo.setBizId(task.getBizId());
        vo.setStatus(task.getStatus());
        vo.setProgress(task.getProgress());
        vo.setCurrentAgent(task.getCurrentAgent());
        vo.setInput(parseJson(task.getInputPayload()));
        vo.setOutput(parseJson(task.getOutputPayload()));
        vo.setErrorMessage(task.getErrorMessage());
        vo.setRetryCount(task.getRetryCount());
        vo.setDurationMs(task.getDurationMs());
        vo.setStartedAt(task.getStartedAt());
        vo.setCompletedAt(task.getCompletedAt());
        vo.setCreatedAt(task.getCreatedAt());
        vo.setUpdatedAt(task.getUpdatedAt());
        return vo;
    }

    /** 空串 / 非法 JSON 都要能安全返回空 Map，否则序列化成 null 会让前端炸。 */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return JSONUtil.parseObj(json).toBean(Map.class);
        } catch (Exception e) {
            return Map.of("_raw", json);
        }
    }

    /** SSE 推送用的精简视图，只带进度相关字段。 */
    @Data
    public static class Progress {
        private String taskId;
        private String status;
        private Integer progress;
        private String currentAgent;

        public Progress(String taskId, String status, Integer progress, String currentAgent) {
            this.taskId = taskId;
            this.status = status;
            this.progress = progress;
            this.currentAgent = currentAgent;
        }
    }
}
