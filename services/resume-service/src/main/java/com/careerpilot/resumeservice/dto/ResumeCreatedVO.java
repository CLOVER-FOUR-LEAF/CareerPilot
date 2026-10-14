package com.careerpilot.resumeservice.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 简历上传成功后返回给前端的视图。
 *
 * <p>{@code resumeId} 是数据库主键（前端拼 /resume/{id} 路径用），
 * {@code resumeBizId} 是业务 ID（跨服务与 Agent 层用）—— 两者含义不同，别混。
 */
@Data
public class ResumeCreatedVO {

    private Long resumeId;
    private String resumeBizId;
    private String versionId;
    private String title;
    /** PENDING / PROCESSING / SUCCESS / FAILED */
    private String parseStatus;
    private String taskId;
    private Integer sectionCount;
    private Integer wordCount;
    private LocalDateTime createdAt;
}
