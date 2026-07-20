package com.aetherlearn.dto;

import lombok.Data;

/**
 * 登录请求参数（F-AUTH-01 登录接口）
 */
@Data
public class LoginRequest {
    /** 登录账号 */
    private String username;
    /** 明文密码（后端用 MD5 校验） */
    private String password;
}
