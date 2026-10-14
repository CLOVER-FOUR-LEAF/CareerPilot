package com.careerpilot.usercenter.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户画像视图。
 *
 * <p>{@code targetCity} / {@code skills} 在库里是 JSON 字符串，
 * 出这个 DTO 时已经解析成数组，前端不用自己 split。
 */
@Data
public class UserProfileDTO {

    private Long userId;
    private String userIdBiz;
    private String email;
    private String phone;
    private String nickname;
    private String avatar;

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
    private List<String> targetCity;
    private Integer targetSalaryMin;
    private Integer targetSalaryMax;

    private List<String> skills;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
