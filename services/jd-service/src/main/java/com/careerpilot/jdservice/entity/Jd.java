package com.careerpilot.jdservice.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * JD 主表。对应 {@code t_jd}。
 *
 * <p>{@code id} 是 REST 路径参数用的数据库主键，{@code jdId} 是跨服务用的业务 ID。
 */
@Data
@TableName("t_jd")
public class Jd {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("jd_id")
    private String jdId;

    @TableField("user_id")
    private String userId;

    /** MANUAL / URL / FILE */
    @TableField("source_type")
    private String sourceType;

    @TableField("source_url")
    private String sourceUrl;

    @TableField("company_name")
    private String companyName;

    @TableField("position_name")
    private String positionName;

    @TableField("salary_range")
    private String salaryRange;

    @TableField("raw_text")
    private String rawText;

    /** PENDING / PROCESSING / SUCCESS / FAILED */
    private String status;

    @TableField("is_deleted")
    private Integer isDeleted;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
