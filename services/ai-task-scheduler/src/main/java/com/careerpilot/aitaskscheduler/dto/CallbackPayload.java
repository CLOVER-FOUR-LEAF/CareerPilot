package com.careerpilot.aitaskscheduler.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 任务执行完成后，ai-task-scheduler 回调业务服务的报文。
 *
 * <p>这些 {@code /internal/**} 接口不经网关，只在内网由调度服务调用。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CallbackPayload {

    private String taskId;

    /** 对应发起方提交的 bizId（jd_id / resume_id / record_id）。 */
    private String bizId;

    private boolean success;

    /** 成功时为 Agent 的原始输出；失败时为 null。 */
    private Map<String, Object> output;

    private String errorMessage;

    private Integer durationMs;
}
