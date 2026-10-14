package com.careerpilot.matchservice.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 匹配记录。对应 {@code t_match_record}。
 *
 * <p>{@code finalScore} 的口径是 {@code 0.4×semantic + 0.35×keyword + 0.25×business}，
 * 这是本项目"可解释打分"的核心口径，写在 Agent 侧，这里只做落库与展示。
 */
@Data
@TableName("t_match_record")
public class MatchRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 业务ID，mch_xxx。REST 路径参数用它。 */
    @TableField("record_id")
    private String recordId;

    @TableField("user_id")
    private String userId;

    /** 关联 t_resume.resume_id */
    @TableField("resume_id")
    private String resumeId;

    /** 关联 t_jd.jd_id */
    @TableField("jd_id")
    private String jdId;

    /** PENDING / PROCESSING / SUCCESS / FAILED */
    private String status;

    @TableField("task_id")
    private String taskId;

    @TableField("semantic_score")
    private Integer semanticScore;

    @TableField("keyword_score")
    private Integer keywordScore;

    @TableField("business_score")
    private Integer businessScore;

    @TableField("final_score")
    private Integer finalScore;

    /** MatchResult 原样，供前端一次性取回 gapList */
    @TableField("result_json")
    private String resultJson;

    @TableField("error_msg")
    private String errorMsg;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("completed_at")
    private LocalDateTime completedAt;
}
