package com.careerpilot.matchservice.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 匹配差距项。对应 {@code t_match_gap}。
 *
 * <p>反范式拆出来是为了让"按差距严重度看 Top N 问题"这类查询走索引，
 * 而不是每次都去解 {@code t_match_record.result_json} 里的 JSON 数组。
 */
@Data
@TableName("t_match_gap")
public class MatchGap {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("record_id")
    private String recordId;

    /** missing_skill / weak_metric / missing_cert */
    @TableField("gap_type")
    private String gapType;

    @TableField("gap_content")
    private String gapContent;

    /** 1-5，越大越紧急。硬性条件不满足固定为 5。 */
    private Integer severity;

    private String suggestion;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
