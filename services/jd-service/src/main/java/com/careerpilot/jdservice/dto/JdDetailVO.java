package com.careerpilot.jdservice.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * JD 详情。
 */
@Data
public class JdDetailVO {

    private Long jdId;
    private String jdBizId;
    private String title;
    private String company;
    private String salaryRange;
    private String rawText;
    private String sourceUrl;
    private String sourceType;
    private String parseStatus;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
