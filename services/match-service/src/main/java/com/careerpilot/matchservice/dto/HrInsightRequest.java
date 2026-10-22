package com.careerpilot.matchservice.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 发起 HR 洞察报告。
 *
 * <p>两个 ID 都是数据库主键（与 match/evaluate 的口径一致）。
 */
@Data
public class HrInsightRequest {

    @NotNull(message = "resumeId 不能为空")
    private Long resumeId;

    @NotNull(message = "jdId 不能为空")
    private Long jdId;

    /** 最多产出多少个验证问题 */
    private Integer maxQuestions = 12;
}
