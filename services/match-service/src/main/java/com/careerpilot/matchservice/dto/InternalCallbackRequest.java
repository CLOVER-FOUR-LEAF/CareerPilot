package com.careerpilot.matchservice.dto;

import lombok.Data;

import java.util.Map;

/**
 * ai-task-scheduler 回调报文。
 */
@Data
public class InternalCallbackRequest {

    private String taskId;

    /** 对应发起方提交的 bizId，这里就是 record_id。 */
    private String bizId;

    private boolean success;

    /** Agent 原始输出：match_score 对应 MatchResult。 */
    private Map<String, Object> output;

    private String errorMessage;

    private Integer durationMs;
}
