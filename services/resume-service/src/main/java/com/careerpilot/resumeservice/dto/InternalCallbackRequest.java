package com.careerpilot.resumeservice.dto;

import lombok.Data;

import java.util.Map;

/**
 * ai-task-scheduler 回调报文。
 *
 * <p>jd-service / resume-service / match-service 三家的回调体形状完全一致，
 * 各服务各自持有一份定义，避免为了共享一个 DTO 而引入跨服务依赖。
 */
@Data
public class InternalCallbackRequest {

    private String taskId;

    /** 对应发起方提交的 bizId，这里就是 resume_id。 */
    private String bizId;

    private boolean success;

    /** Agent 原始输出：resume_analyze 对应 AnalysisResult。 */
    private Map<String, Object> output;

    private String errorMessage;

    private Integer durationMs;
}
