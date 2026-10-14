package com.careerpilot.jdservice.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * JD 新建成功后返回给前端的视图。
 *
 * <p>{@code jdId} 是数据库主键（前端拼 /jd/{id} 路径用），
 * {@code jdBizId} 是业务 ID（跨服务与 Agent 层用）—— 两者含义不同，别混。
 */
@Data
public class JdCreatedVO {

    private Long jdId;
    private String jdBizId;
    private String title;
    private String company;
    private String parseStatus;
    private String taskId;
    private LocalDateTime createdAt;

    public static JdCreatedVO of(com.careerpilot.jdservice.entity.Jd jd, String taskId) {
        JdCreatedVO vo = new JdCreatedVO();
        vo.setJdId(jd.getId());
        vo.setJdBizId(jd.getJdId());
        vo.setTitle(jd.getPositionName());
        vo.setCompany(jd.getCompanyName());
        vo.setParseStatus(jd.getStatus());
        vo.setTaskId(taskId);
        vo.setCreatedAt(jd.getCreatedAt());
        return vo;
    }
}
