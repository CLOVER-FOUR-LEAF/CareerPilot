package com.careerpilot.aitaskscheduler.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

/**
 * 创建 AI 任务的请求。
 *
 * <p>{@code input} 是结构化对象而不是字符串：调用方（jd-service / resume-service /
 * match-service / 前端）不应该被迫手工拼 JSON 字符串。
 */
@Data
public class CreateTaskRequest {

    @NotBlank(message = "taskType 不能为空")
    private String taskType;

    /** 业务对象ID：jd_id / resume_id / record_id，取决于 taskType。 */
    private String bizId;

    @NotNull(message = "input 不能为空")
    private Map<String, Object> input;
}
