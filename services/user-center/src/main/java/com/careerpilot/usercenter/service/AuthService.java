package com.careerpilot.usercenter.service;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.careerpilot.usercenter.dto.ApiResponse;
import com.careerpilot.usercenter.dto.LoginRequest;
import com.careerpilot.usercenter.dto.LoginResponse;
import com.careerpilot.usercenter.dto.RefreshRequest;
import com.careerpilot.usercenter.dto.RegisterRequest;
import com.careerpilot.usercenter.entity.Membership;
import com.careerpilot.usercenter.entity.User;
import com.careerpilot.usercenter.entity.UserProfile;
import com.careerpilot.usercenter.mapper.MembershipMapper;
import com.careerpilot.usercenter.mapper.UserMapper;
import com.careerpilot.usercenter.mapper.UserProfileMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.UUID;

/**
 * 认证与令牌。
 *
 * <p>登出用的是 refreshToken 的 jti 黑名单（存 Redis，TTL 等于 token 剩余寿命）。
 * 之所以不黑名单 accessToken：JWT 是无状态的，网关每请求都要查一次 Redis，
 * 会把网关变成瓶颈；而 refreshToken 才是真正的长期凭证。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String BLACKLIST_KEY_PREFIX = "auth:blacklist:";

    /** 免费用户注册赠送的 AI 调用次数。 */
    private static final int FREE_AI_QUOTA = 3;

    private final UserMapper userMapper;
    private final UserProfileMapper userProfileMapper;
    private final MembershipMapper membershipMapper;
    private final StringRedisTemplate redisTemplate;

    @Value("${jwt.secret:careerpilot-default-secret-key-must-be-at-least-256-bits-long-for-hs256}")
    private String jwtSecret;

    @Value("${jwt.access-token-expiration:7200}")
    private int accessTokenExpiration;

    @Value("${jwt.refresh-token-expiration:2592000}")
    private int refreshTokenExpiration;

    /** BCrypt 强度 10 是安全与登录延迟的平衡点；调高会明显拖慢注册/登录。 */
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(10);

    // ── 注册 ────────────────────────────────────────────────────

    /**
     * 注册并直接返回登录态。
     *
     * <p>原来这里只返回用户信息、没有 token，前端注册完还得弹回登录页让用户再输一遍密码，
     * 是很明显的体验断裂。
     */
    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<LoginResponse> register(RegisterRequest request) {
        Long existing = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getEmail, request.getEmail()));
        if (existing != null && existing > 0) {
            return ApiResponse.error(1409, "邮箱已被注册");
        }

        String userId = "usr_" + IdUtil.fastSimpleUUID().substring(0, 16);
        LocalDateTime now = LocalDateTime.now();

        User user = new User();
        user.setUserId(userId);
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setNickname(resolveNickname(request));
        user.setStatus(1);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        userMapper.insert(user);

        // 初始化画像：只落 user_id，其余字段留给用户后续填
        UserProfile profile = new UserProfile();
        profile.setUserId(userId);
        profile.setCreatedAt(now);
        profile.setUpdatedAt(now);
        userProfileMapper.insert(profile);

        // 初始化会员：免费额度
        Membership membership = new Membership();
        membership.setUserId(userId);
        membership.setLevel(0);
        membership.setAiQuotaRemaining(FREE_AI_QUOTA);
        membership.setAiQuotaTotal(FREE_AI_QUOTA);
        membership.setAutoRenew(0);
        membership.setCreatedAt(now);
        membership.setUpdatedAt(now);
        membershipMapper.insert(membership);

        user.setLastLoginAt(now);
        userMapper.updateById(user);

        return ApiResponse.success(buildLoginResponse(user, membership));
    }

    private static String resolveNickname(RegisterRequest request) {
        if (request.getNickname() != null && !request.getNickname().isBlank()) {
            return request.getNickname().trim();
        }
        String email = request.getEmail();
        int at = email.indexOf('@');
        return at > 0 ? email.substring(0, at) : email;
    }

    // ── 登录 ────────────────────────────────────────────────────

    public ApiResponse<LoginResponse> login(LoginRequest request) {
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getEmail, request.getEmail()));
        if (user == null) {
            // 不区分「账号不存在」和「密码错误」，避免账号枚举
            return ApiResponse.error(1401, "账号或密码错误");
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            return ApiResponse.error(1403, "账号已被封禁");
        }
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            return ApiResponse.error(1402, "账号或密码错误");
        }

        user.setLastLoginAt(LocalDateTime.now());
        userMapper.updateById(user);

        Membership membership = findMembership(user.getUserId());
        return ApiResponse.success(buildLoginResponse(user, membership));
    }

    /**
     * 登出：把 refreshToken 加入黑名单。
     *
     * <p>accessToken 在有效期内仍可用（无状态 JWT 的固有取舍），
     * 想要立即失效需要网关查黑名单，这里刻意不做那笔开销。
     */
    public ApiResponse<Void> logout(String userId, RefreshRequest request) {
        if (request == null || request.getRefreshToken() == null || request.getRefreshToken().isBlank()) {
            return ApiResponse.success();
        }
        try {
            Claims claims = parseRefreshToken(request.getRefreshToken());
            String jti = claims.getId();
            if (jti != null) {
                long remainingMs = claims.getExpiration().getTime() - System.currentTimeMillis();
                if (remainingMs > 0) {
                    redisTemplate.opsForValue().set(
                            BLACKLIST_KEY_PREFIX + jti, "1",
                            Duration.ofMillis(remainingMs));
                }
            }
            if (claims.getSubject() != null) {
                log.debug("登出用户: {}", claims.getSubject());
            }
            log.info("用户登出: userId={}", claims.getSubject());
        } catch (Exception e) {
            // token 本来就无效，登出照样算成功
            log.debug("登出时 token 解析失败，忽略: {}", e.getMessage());
        }
        return ApiResponse.success();
    }

    // ── 刷新 ────────────────────────────────────────────────────

    public ApiResponse<LoginResponse> refreshToken(RefreshRequest request) {
        Claims claims;
        try {
            claims = parseRefreshToken(request.getRefreshToken());
        } catch (Exception e) {
            return ApiResponse.error(1401, "Refresh token 无效或已过期");
        }

        String jti = claims.getId();
        if (jti != null && Boolean.TRUE.equals(redisTemplate.hasKey(BLACKLIST_KEY_PREFIX + jti))) {
            return ApiResponse.error(1401, "Refresh token 已失效，请重新登录");
        }

        String userId = claims.getSubject();
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUserId, userId));
        if (user == null) {
            return ApiResponse.error(1401, "用户不存在");
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            return ApiResponse.error(1403, "账号已被封禁");
        }

        return ApiResponse.success(LoginResponse.builder()
                .accessToken(generateAccessToken(userId))
                // 刷新时不轮换 refreshToken，保持前端只需存一份
                .refreshToken(request.getRefreshToken())
                .tokenType("Bearer")
                .expiresIn(accessTokenExpiration)
                .user(buildUserInfo(user, findMembership(userId)))
                .build());
    }

    // ── 令牌 ────────────────────────────────────────────────────

    private Claims parseRefreshToken(String token) {
        return Jwts.parser()
                .verifyWith(refreshKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private String generateAccessToken(String userId) {
        Date now = new Date();
        return Jwts.builder()
                .subject(userId)
                .issuer("careerpilot")
                .issuedAt(now)
                .expiration(new Date(now.getTime() + accessTokenExpiration * 1000L))
                .id(UUID.randomUUID().toString())
                .signWith(accessKey())
                .compact();
    }

    private String generateRefreshToken(String userId) {
        Date now = new Date();
        return Jwts.builder()
                .subject(userId)
                .issuer("careerpilot")
                .issuedAt(now)
                .expiration(new Date(now.getTime() + refreshTokenExpiration * 1000L))
                .id(UUID.randomUUID().toString())
                .signWith(refreshKey())
                .compact();
    }

    /**
     * refreshToken 用不同的密钥签名。
     *
     * <p>否则一个泄露的 refreshToken 就能被当成 accessToken 直接访问业务接口。
     */
    private SecretKey accessKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    private SecretKey refreshKey() {
        return Keys.hmacShaKeyFor(("refresh-" + jwtSecret).getBytes(StandardCharsets.UTF_8));
    }

    // ── 组装 ────────────────────────────────────────────────────

    private LoginResponse buildLoginResponse(User user, Membership membership) {
        return LoginResponse.builder()
                .accessToken(generateAccessToken(user.getUserId()))
                .refreshToken(generateRefreshToken(user.getUserId()))
                .tokenType("Bearer")
                .expiresIn(accessTokenExpiration)
                .user(buildUserInfo(user, membership))
                .build();
    }

    private LoginResponse.UserInfo buildUserInfo(User user, Membership membership) {
        return LoginResponse.UserInfo.builder()
                .userId(user.getId())
                .userIdBiz(user.getUserId())
                .email(user.getEmail())
                .nickname(user.getNickname())
                .avatar(user.getAvatarUrl())
                .membershipLevel(levelName(membership))
                .build();
    }

    private Membership findMembership(String userId) {
        return membershipMapper.selectOne(
                new LambdaQueryWrapper<Membership>().eq(Membership::getUserId, userId));
    }

    static String levelName(Membership membership) {
        if (membership == null || membership.getLevel() == null || membership.getLevel() == 0) {
            return "FREE";
        }
        return membership.getLevel() == 1 ? "PRO" : "ENTERPRISE";
    }
}
