package com.careerpilot.matchservice.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 面试追问记录。对应 {@code t_verification_record}。
 *
 * <p>这张表是合规要求 C-6「人工决策留痕」的落点：
 * 面试官每录入一次回答、每得到一次判定，都要留痕。
 *
 * <p>{@code coverage} 只允许三个值：
 * demonstrated / adjacent / not_evidenced（R1）。
 * 注意 {@code not_evidenced} 的含义是「本次提问未获得证据」，
 * **不是**「候选人不具备该能力」。
 */
@Data
@TableName("t_verification_record")
public class VerificationRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 业务ID，ver_xxx。 */
    @TableField("record_id")
    private String recordId;

    @TableField("insight_id")
    private String insightId;

    /** 对应报告里的验证问题 id（vq#1） */
    @TableField("vq_id")
    private String vqId;

    @TableField("round_no")
    private Integer roundNo;

    @TableField("question")
    private String question;

    @TableField("answer")
    private String answer;

    /** demonstrated / adjacent / not_evidenced */
    private String coverage;

    private BigDecimal confidence;

    private String reason;

    /** 回答里的原文片段（已由 Agent 侧校验过确实存在） */
    @TableField("evidence_ref")
    private String evidenceRef;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
