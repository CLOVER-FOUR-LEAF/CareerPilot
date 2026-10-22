package com.careerpilot.matchservice.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * HR 侧洞察报告。对应 {@code t_hr_insight}。
 *
 * <p>一份报告 = 一份 JD 与一份简历的完整分析：能力维度模型 +
 * gap → 验证问题映射 + 完整报告。
 */
@Data
@TableName("t_hr_insight")
public class HrInsight {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 业务ID，ins_xxx。REST 路径参数用它。 */
    @TableField("insight_id")
    private String insightId;

    @TableField("task_id")
    private String taskId;

    @TableField("user_id")
    private String userId;

    /** 关联 t_jd.jd_id */
    @TableField("jd_id")
    private String jdId;

    /** 关联 t_resume.resume_id */
    @TableField("resume_id")
    private String resumeId;

    /** RECRUITER / HR_VIEW */
    private String mode;

    /** 能力维度模型 */
    @TableField("capability_json")
    private String capabilityJson;

    /** gap → 验证问题映射 */
    @TableField("gap_plan_json")
    private String gapPlanJson;

    /** 完整报告（含代码拼接的免责声明） */
    @TableField("report_json")
    private String reportJson;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
