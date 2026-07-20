package com.aetherlearn.service;

import com.aetherlearn.dto.LoginRequest;
import com.aetherlearn.dto.LoginResponse;

/**
 * 认证服务接口（F-AUTH-01 登录 / F-AUTH-02 退出）
 */
public interface AuthService {

    /**
     * 登录：校验账号密码（MD5），成功签发 JWT
     *
     * @param request 登录参数（账号 + 明文密码）
     * @return 含 Token 与用户档案的响应
     */
    LoginResponse login(LoginRequest request);
}
