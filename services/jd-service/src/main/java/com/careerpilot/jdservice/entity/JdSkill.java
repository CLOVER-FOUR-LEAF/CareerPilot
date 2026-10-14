package com.careerpilot.jdservice.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("t_jd_skill")
public class JdSkill {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("jd_id")
    private String jdId;

    private String skillType;
    private String skillName;
    private BigDecimal weight;
    private LocalDateTime createdAt;
}
