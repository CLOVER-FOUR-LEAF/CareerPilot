package com.careerpilot.matchservice.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 匹配评估提交结果。
 */
@Data
public class MatchCreatedVO {

    private String recordId;
    private String status;
    private String taskId;
    private LocalDateTime createdAt;

    public static MatchCreatedVO of(String recordId, String status, String taskId, LocalDateTime createdAt) {
        MatchCreatedVO vo = new MatchCreatedVO();
        vo.setRecordId(recordId);
        vo.setStatus(status);
        vo.setTaskId(taskId);
        vo.setCreatedAt(createdAt);
        return vo;
    }
}
