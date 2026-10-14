package com.careerpilot.resumeservice.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 简历版本。对应 {@code t_resume_version}。
 *
 * <p>{@code parsedText} 存从文件抽出的正文，{@code analysisJson} 存 Agent 返回的
 * AnalysisResult 原样 —— 这两个字段让「重新解析」不需要重新上传文件，
 * 也是 match-service 做语义匹配的输入来源。
 */
@Data
@TableName("t_resume_version")
public class ResumeVersion {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("version_id")
    private String versionId;

    @TableField("resume_id")
    private String resumeId;

    @TableField("version_no")
    private Integer versionNo;

    @TableField("parsed_text")
    private String parsedText;

    @TableField("analysis_json")
    private String analysisJson;

    @TableField("ats_score")
    private Integer atsScore;

    @TableField("change_note")
    private String changeNote;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
