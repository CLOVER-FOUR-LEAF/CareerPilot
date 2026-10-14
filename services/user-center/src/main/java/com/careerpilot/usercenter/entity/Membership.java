package com.careerpilot.usercenter.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会员权益。对应 {@code t_membership}。
 */
@Data
@TableName("t_membership")
public class Membership {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private String userId;

    /** 0=免费 1=Pro 2=企业版 */
    private Integer level;

    @TableField("expire_at")
    private LocalDateTime expireAt;

    @TableField("ai_quota_remaining")
    private Integer aiQuotaRemaining;

    @TableField("ai_quota_total")
    private Integer aiQuotaTotal;

    @TableField("auto_renew")
    private Integer autoRenew;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
