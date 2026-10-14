package com.careerpilot.usercenter.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("t_user_profile")
public class UserProfile {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private String userId;

    private String gender;
    private Integer birthYear;
    private String city;
    private String currentTitle;
    private Integer yearsOfExperience;
    private String educationLevel;
    private String school;
    private String major;
    private Integer graduationYear;
    private String targetIndustry;
    private String targetPosition;
    private String targetSeniority;
    private String targetCityJson;
    private Integer targetSalaryMin;
    private Integer targetSalaryMax;
    private String skillsJson;
    private String preferencesJson;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
