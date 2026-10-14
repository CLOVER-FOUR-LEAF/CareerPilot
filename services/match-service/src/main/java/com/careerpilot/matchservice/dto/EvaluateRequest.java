package com.careerpilot.matchservice.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 发起一次匹配评估。{@code resumeId} / {@code jdId} 都是数据库主键。
 */
@Data
public class EvaluateRequest {

    @NotNull(message = "请选择简历")
    private Long resumeId;

    @NotNull(message = "请选择目标 JD")
    private Long jdId;
}
