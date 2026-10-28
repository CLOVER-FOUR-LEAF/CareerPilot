package com.careerpilot.usercenter.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("t_user")
public class User {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private String userId;

    private String email;
    private String phone;
    private String passwordHash;
    private String nickname;
    private String avatarUrl;
    private Integer status;
    /** USER / RECRUITER / ADMIN —— 授权维度，不是账号类型 */
    private String role;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
