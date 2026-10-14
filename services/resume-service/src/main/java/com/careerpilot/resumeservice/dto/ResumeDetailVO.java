package com.careerpilot.resumeservice.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 简历详情。
 */
@Data
public class ResumeDetailVO {

    private Long resumeId;
    private String resumeBizId;
    private String title;
    private Integer atsScore;
    /** PENDING / PROCESSING / SUCCESS / FAILED */
    private String parseStatus;
    /** 从文件抽出的正文，供前端预览与后续 Agent 复用 */
    private String textContent;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
