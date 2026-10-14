package com.careerpilot.usercenter.controller;

import com.careerpilot.usercenter.dto.ApiResponse;
import com.careerpilot.usercenter.dto.LoginRequest;
import com.careerpilot.usercenter.dto.LoginResponse;
import com.careerpilot.usercenter.dto.RefreshRequest;
import com.careerpilot.usercenter.dto.RegisterRequest;
import com.careerpilot.usercenter.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /** 注册并直接返回登录态。 */
    @PostMapping("/register")
    public ApiResponse<LoginResponse> register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    /**
     * 登出。前端需要把 refreshToken 一起带上来，否则服务端没法把它拉黑。
     *
     * <p>不强制要求 {@code X-User-Id}：登出接口必须在 token 可能已部分失效时也能调通。
     */
    @PostMapping("/logout")
    public ApiResponse<Void> logout(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestBody(required = false) RefreshRequest request) {
        return authService.logout(userId, request);
    }

    @PostMapping("/refresh")
    public ApiResponse<LoginResponse> refresh(@RequestBody RefreshRequest request) {
        return authService.refreshToken(request);
    }
}
