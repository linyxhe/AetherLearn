package com.aetherlearn.dto;

import lombok.Data;

/**
 * 登录响应（F-AUTH-01 登录接口）
 * <p>返回 JWT Token 与用户档案（角色用于前端路由跳转）。</p>
 */
@Data
public class LoginResponse {
    /** JWT Token（含 Bearer 前缀） */
    private String token;
    /** 用户档案 */
    private UserProfile user;
}
