package com.aetherlearn.controller;

import com.aetherlearn.common.Result;
import com.aetherlearn.common.SecurityUtils;
import com.aetherlearn.dto.UpdateUserRequest;
import com.aetherlearn.dto.UserProfile;
import com.aetherlearn.service.UserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户控制器（F-AUTH-02 用户信息维护）
 * <p>路径：/api/user/** ，需登录后访问（由 JWT 过滤器鉴权）。</p>
 */
@RestController
@RequestMapping("/api/user")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * 获取当前登录用户信息
     */
    @GetMapping("/info")
    public Result<UserProfile> info() {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(userService.getInfo(userId));
    }

    /**
     * 更新当前登录用户信息（昵称/头像/邮箱/手机号/可选改密）
     */
    @PutMapping("/update")
    public Result<UserProfile> update(@Valid @RequestBody UpdateUserRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success("更新成功", userService.update(userId, request));
    }
}
