package com.careerpilot.usercenter.controller;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.careerpilot.usercenter.dto.ApiResponse;
import com.careerpilot.usercenter.dto.UserProfileDTO;
import com.careerpilot.usercenter.entity.Membership;
import com.careerpilot.usercenter.entity.User;
import com.careerpilot.usercenter.entity.UserProfile;
import com.careerpilot.usercenter.mapper.MembershipMapper;
import com.careerpilot.usercenter.mapper.UserMapper;
import com.careerpilot.usercenter.mapper.UserProfileMapper;
import com.careerpilot.usercenter.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户画像与会员信息。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/user")
@RequiredArgsConstructor
public class UserController {

    private final UserMapper userMapper;
    private final UserProfileMapper userProfileMapper;
    private final MembershipMapper membershipMapper;

    @GetMapping("/profile")
    public ApiResponse<UserProfileDTO> getProfile(@RequestHeader("X-User-Id") String userId) {
        User user = requireUser(userId);
        UserProfile profile = findProfile(userId);

        UserProfileDTO dto = new UserProfileDTO();
        dto.setUserId(user.getId());
        dto.setUserIdBiz(user.getUserId());
        dto.setEmail(user.getEmail());
        dto.setPhone(maskPhone(user.getPhone()));
        dto.setNickname(user.getNickname());
        dto.setAvatar(user.getAvatarUrl());
        dto.setRole(AuthService.normalizeRole(user.getRole()));
        dto.setCreatedAt(user.getCreatedAt());
        dto.setUpdatedAt(user.getUpdatedAt());

        if (profile != null) {
            dto.setGender(profile.getGender());
            dto.setBirthYear(profile.getBirthYear());
            dto.setCity(profile.getCity());
            dto.setCurrentTitle(profile.getCurrentTitle());
            dto.setYearsOfExperience(profile.getYearsOfExperience());
            dto.setEducationLevel(profile.getEducationLevel());
            dto.setSchool(profile.getSchool());
            dto.setMajor(profile.getMajor());
            dto.setGraduationYear(profile.getGraduationYear());
            dto.setTargetIndustry(profile.getTargetIndustry());
            dto.setTargetPosition(profile.getTargetPosition());
            dto.setTargetSeniority(profile.getTargetSeniority());
            dto.setTargetCity(parseStringArray(profile.getTargetCityJson()));
            dto.setTargetSalaryMin(profile.getTargetSalaryMin());
            dto.setTargetSalaryMax(profile.getTargetSalaryMax());
            dto.setSkills(parseStringArray(profile.getSkillsJson()));
        }
        return ApiResponse.success(dto);
    }

    /**
     * 更新画像。
     *
     * <p>逐字段判空后覆盖，所以前端可以只提交变化的字段。
     * 原来的写法用 {@code profile.getId() == null} 判断「要不要新建」，
     * 但从 DB 查出来的对象 id 一定有值，而新建分支又在前面刚查过，
     * 实际上永远走不到 insert。
     */
    @PutMapping("/profile")
    public ApiResponse<UserProfileDTO> updateProfile(@RequestHeader("X-User-Id") String userId,
                                                     @RequestBody UserProfileDTO request) {
        User user = requireUser(userId);

        if (request.getNickname() != null && !request.getNickname().isBlank()) {
            user.setNickname(request.getNickname().trim());
            user.setUpdatedAt(LocalDateTime.now());
            userMapper.updateById(user);
        }

        UserProfile profile = findProfile(userId);
        boolean isNew = profile == null;
        if (isNew) {
            profile = new UserProfile();
            profile.setUserId(userId);
            profile.setCreatedAt(LocalDateTime.now());
        }

        if (request.getGender() != null) profile.setGender(request.getGender());
        if (request.getBirthYear() != null) profile.setBirthYear(request.getBirthYear());
        if (request.getCity() != null) profile.setCity(request.getCity());
        if (request.getCurrentTitle() != null) profile.setCurrentTitle(request.getCurrentTitle());
        if (request.getYearsOfExperience() != null) profile.setYearsOfExperience(request.getYearsOfExperience());
        if (request.getEducationLevel() != null) profile.setEducationLevel(request.getEducationLevel());
        if (request.getSchool() != null) profile.setSchool(request.getSchool());
        if (request.getMajor() != null) profile.setMajor(request.getMajor());
        if (request.getGraduationYear() != null) profile.setGraduationYear(request.getGraduationYear());
        if (request.getTargetIndustry() != null) profile.setTargetIndustry(request.getTargetIndustry());
        if (request.getTargetPosition() != null) profile.setTargetPosition(request.getTargetPosition());
        if (request.getTargetSeniority() != null) profile.setTargetSeniority(request.getTargetSeniority());
        if (request.getTargetCity() != null) profile.setTargetCityJson(toJson(request.getTargetCity()));
        if (request.getSkills() != null) profile.setSkillsJson(toJson(request.getSkills()));
        // 薪资下限允许显式置 0，所以这里判 != null 而不是判非空
        if (request.getTargetSalaryMin() != null) profile.setTargetSalaryMin(request.getTargetSalaryMin());
        if (request.getTargetSalaryMax() != null) profile.setTargetSalaryMax(request.getTargetSalaryMax());
        profile.setUpdatedAt(LocalDateTime.now());

        if (isNew) {
            userProfileMapper.insert(profile);
        } else {
            userProfileMapper.updateById(profile);
        }

        return getProfile(userId);
    }

    @GetMapping("/membership")
    public ApiResponse<Map<String, Object>> getMembership(@RequestHeader("X-User-Id") String userId) {
        Membership membership = membershipMapper.selectOne(
                new LambdaQueryWrapper<Membership>().eq(Membership::getUserId, userId));

        int level = membership == null || membership.getLevel() == null ? 0 : membership.getLevel();

        Map<String, Object> result = new HashMap<>();
        result.put("level", levelName(level));
        result.put("levelName", levelLabel(level));
        result.put("expireAt", membership == null ? null : membership.getExpireAt());
        result.put("autoRenew", membership != null && membership.getAutoRenew() != null
                && membership.getAutoRenew() == 1);
        result.put("aiQuotaRemaining", membership == null || membership.getAiQuotaRemaining() == null
                ? 0 : membership.getAiQuotaRemaining());
        result.put("aiQuotaTotal", membership == null || membership.getAiQuotaTotal() == null
                ? 0 : membership.getAiQuotaTotal());
        return ApiResponse.success(result);
    }

    // ── 内部 ────────────────────────────────────────────────────

    private User requireUser(String userId) {
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUserId, userId));
        if (user == null) {
            throw new UserNotFoundException(userId);
        }
        return user;
    }

    private UserProfile findProfile(String userId) {
        return userProfileMapper.selectOne(
                new LambdaQueryWrapper<UserProfile>().eq(UserProfile::getUserId, userId));
    }

    private static String levelName(int level) {
        return switch (level) {
            case 1 -> "PRO";
            case 2 -> "ENTERPRISE";
            default -> "FREE";
        };
    }

    private static String levelLabel(int level) {
        return switch (level) {
            case 1 -> "专业版";
            case 2 -> "企业版";
            default -> "免费版";
        };
    }

    private static String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(7);
    }

    private static String toJson(List<String> values) {
        return JSONUtil.toJsonStr(values == null ? new ArrayList<>() : values);
    }

    /**
     * 解析库里存的 JSON 数组字符串。
     *
     * <p>对历史脏数据保持宽容：解析不出来就返回空列表，
     * 不能因为一个字段格式不对就让整个画像接口 500。
     */
    private static List<String> parseStringArray(String json) {
        List<String> result = new ArrayList<>();
        if (json == null || json.isBlank()) {
            return result;
        }
        try {
            Object parsed = JSONUtil.parse(json);
            if (parsed instanceof JSONArray array) {
                for (Object o : array) {
                    if (o != null && !String.valueOf(o).isBlank()) {
                        result.add(String.valueOf(o));
                    }
                }
            }
        } catch (Exception e) {
            log.warn("画像 JSON 字段解析失败，返回空列表: {}", json);
        }
        return result;
    }

    public static class UserNotFoundException extends RuntimeException {
        public UserNotFoundException(String userId) {
            super("用户不存在: " + userId);
        }
    }
}
