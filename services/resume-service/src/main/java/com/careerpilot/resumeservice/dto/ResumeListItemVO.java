package com.careerpilot.resumeservice.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 简历列表项。
 */
@Data
public class ResumeListItemVO {

    private Long resumeId;
    private String resumeBizId;
    private String title;
    private Integer atsScore;
    /** PENDING / PROCESSING / SUCCESS / FAILED */
    private String parseStatus;
    private Boolean isPrimary;
    private LocalDateTime updatedAt;
}
