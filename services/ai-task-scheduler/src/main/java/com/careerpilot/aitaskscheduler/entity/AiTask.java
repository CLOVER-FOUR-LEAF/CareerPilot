package com.careerpilot.aitaskscheduler.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 任务主表。
 *
 * <p>字段与 {@code db/migrations/V1__init_schema.sql} 的 {@code t_ai_task} 严格一一对应。
 * {@code inputPayload} / {@code outputPayload} 以字符串形式读写 MySQL 的 JSON 列，
 * 进出 REST 层时再由 Service 层与 {@code Map<String,Object>} 互转。
 *
 * <p>状态取值：PENDING / RUNNING / SUCCESS / FAILED / CANCELLED。
 */
@Data
@TableName("t_ai_task")
public class AiTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 业务ID，task_xxx。所有跨服务引用都用它，不用自增主键。 */
    private String taskId;

    private String userId;

    /** jd_parse / resume_analyze / match_score / resume_optimize / interview / cover_letter / career_plan */
    private String taskType;

    /** 业务对象ID：resume_id / jd_id / record_id，取决于 taskType。 */
    private String bizId;

    private String status;

    private Integer progress;

    private String currentAgent;

    @TableField("input_payload")
    private String inputPayload;

    @TableField("output_payload")
    private String outputPayload;

    @TableField("error_msg")
    private String errorMessage;

    private String traceId;

    private Integer retryCount;

    private Integer durationMs;

    private LocalDateTime startedAt;

    private LocalDateTime completedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    /** 是否处于终态 —— SSE 轮询据此决定是否关闭流。 */
    public boolean isTerminal() {
        return "SUCCESS".equals(status)
                || "FAILED".equals(status)
                || "CANCELLED".equals(status);
    }
}
