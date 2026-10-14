package com.careerpilot.aitaskscheduler.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 单次 Agent 调用日志。
 *
 * <p>对应 {@code t_agent_invocation}。一个任务可能触发多次 Agent
 * （例如 interview 任务分 question / evaluate 两阶段），用 {@code invocationNo} 区分。
 */
@Data
@TableName("t_agent_invocation")
public class AgentInvocation {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 业务ID，inv_xxx。 */
    private String invocationId;

    private String taskId;

    private String agentName;

    /** 同一任务内的调用序号，从 1 开始。 */
    private Integer invocationNo;

    private String modelName;

    /** RUNNING / SUCCESS / FAILED */
    private String status;

    @TableField("input_payload")
    private String inputPayload;

    @TableField("output_payload")
    private String outputPayload;

    private Integer durationMs;

    private Integer retryAttempt;

    @TableField("error_msg")
    private String errorMessage;

    private LocalDateTime startedAt;

    private LocalDateTime completedAt;

    private LocalDateTime createdAt;
}
