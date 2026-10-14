package com.careerpilot.jdservice.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * JD 解析结果。对应 {@code t_jd_parse_result}。
 *
 * <p>{@code rawResultJson} 存 Agent 返回的 JdChunk 原样结构，前端直接消费；
 * 其余字段是从中抽出来的派生列，只为检索和统计服务。
 */
@Data
@TableName("t_jd_parse_result")
public class JdParseResult {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("jd_id")
    private String jdId;

    @TableField("raw_result_json")
    private String rawResultJson;

    @TableField("hard_skills_json")
    private String hardSkillsJson;

    @TableField("soft_skills_json")
    private String softSkillsJson;

    @TableField("biz_metrics_json")
    private String bizMetricsJson;

    @TableField("responsibilities_json")
    private String responsibilitiesJson;

    @TableField("requirements_json")
    private String requirementsJson;

    private String seniority;

    private String industry;

    private String location;

    @TableField("salary_min")
    private BigDecimal salaryMin;

    @TableField("salary_max")
    private BigDecimal salaryMax;

    @TableField("experience_min_years")
    private BigDecimal experienceMinYears;

    @TableField("education_required")
    private String educationRequired;

    @TableField("parse_score")
    private BigDecimal parseScore;

    @TableField("parse_model")
    private String parseModel;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
