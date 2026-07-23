package com.aetherlearn.controller;

import com.aetherlearn.common.Result;
import com.aetherlearn.dto.LoginRequest;
import com.aetherlearn.dto.LoginResponse;
import com.aetherlearn.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证控制器（F-AUTH-01 登录接口）
 * <p>路径：POST /api/auth/login —— 该接口在 SecurityConfig 中放行，无需 Token。</p>
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * 登录：校验账号密码（MD5），签发 JWT
     */
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return Result.success("登录成功", response);
    }

    /**
     * 退出登录：JWT 无状态，前端清除 token 即可；此端点预留用于未来扩展（如 token 黑名单）
     */
    @PostMapping("/logout")
    public Result<Void> logout() {
        return Result.success("退出成功", null);
    }
}
