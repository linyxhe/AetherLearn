package com.aetherlearn.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 登录请求参数（F-AUTH-01 登录接口）
 */
@Data
public class LoginRequest {
    /** 登录账号 */
    @NotBlank(message = "请输入用户名")
    private String username;
    /** 明文密码（后端用 MD5 校验） */
    @NotBlank(message = "请输入密码")
    private String password;
}
