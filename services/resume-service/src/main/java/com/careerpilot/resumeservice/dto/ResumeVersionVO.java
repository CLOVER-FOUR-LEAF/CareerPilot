package com.careerpilot.resumeservice.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 简历版本列表项。
 */
@Data
public class ResumeVersionVO {

    private String versionId;
    private Integer versionNo;
    private Integer atsScore;
    private String changeNote;
    private LocalDateTime createdAt;
}
