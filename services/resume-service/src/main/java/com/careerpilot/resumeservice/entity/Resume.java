package com.careerpilot.resumeservice.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 简历主表。对应 {@code t_resume}。
 */
@Data
@TableName("t_resume")
public class Resume {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("resume_id")
    private String resumeId;

    @TableField("user_id")
    private String userId;

    private String title;

    @TableField("current_version_id")
    private String currentVersionId;

    @TableField("ats_score")
    private Integer atsScore;

    /** PENDING / PROCESSING / SUCCESS / FAILED */
    @TableField("parse_status")
    private String parseStatus;

    @TableField("file_id")
    private String fileId;

    @TableField("is_primary")
    private Integer isPrimary;

    @TableField("is_deleted")
    private Integer isDeleted;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
