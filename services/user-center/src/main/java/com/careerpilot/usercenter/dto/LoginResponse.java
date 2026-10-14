package com.careerpilot.usercenter.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 登录 / 注册成功后的返回体。
 *
 * <p>注册也复用这个结构：注册完直接把 token 给前端，用户不需要再走一遍登录。
 */
@Data
@Builder
public class LoginResponse {

    private String accessToken;
    private String refreshToken;
    private String tokenType;
    private int expiresIn;
    private UserInfo user;

    @Data
    @Builder
    public static class UserInfo {
        /** 数据库主键 t_user.id */
        private Long userId;
        /** 业务ID usr_xxx，跨服务用 */
        private String userIdBiz;
        private String email;
        private String nickname;
        private String avatar;
        /** FREE / PRO / ENTERPRISE */
        private String membershipLevel;
    }
}
