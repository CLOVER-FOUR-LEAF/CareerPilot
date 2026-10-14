package com.careerpilot.resumeservice.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 简历章节。对应 {@code t_resume_section}。
 */
@Data
@TableName("t_resume_section")
public class ResumeSection {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("resume_id")
    private String resumeId;

    @TableField("version_id")
    private String versionId;

    /** summary/experience/project/skill/education/certificate/other */
    @TableField("section_type")
    private String sectionType;

    @TableField("section_order")
    private Integer sectionOrder;

    /** 原始标题行，例如「项目经历」 */
    @TableField("section_title")
    private String sectionTitle;

    @TableField("content_text")
    private String contentText;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
