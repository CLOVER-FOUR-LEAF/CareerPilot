package com.careerpilot.jdservice.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * JD 列表项。列表不返回 rawText 全文（可能很长），只给摘要。
 */
@Data
public class JdListItemVO {

    private Long jdId;
    private String jdBizId;
    private String title;
    private String company;
    /** PENDING / PROCESSING / SUCCESS / FAILED */
    private String parseStatus;
    private String sourceUrl;
    private String rawTextPreview;
    private LocalDateTime createdAt;
}
