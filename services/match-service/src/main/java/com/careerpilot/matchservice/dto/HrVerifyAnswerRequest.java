package com.careerpilot.matchservice.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 提交面试官录入的回答。
 *
 * <p>判定所需的「问题 + 判定信号」由服务端从已保存的报告里取，
 * 不接受前端传入 —— 否则前端可以改信号来让判定对自己有利。
 */
@Data
public class HrVerifyAnswerRequest {

    @NotBlank(message = "insightId 不能为空")
    private String insightId;

    @NotBlank(message = "vqId 不能为空")
    private String vqId;

    @NotBlank(message = "answer 不能为空")
    private String answer;

    /** 第几轮追问，从 1 开始 */
    private Integer roundNo = 1;
}
