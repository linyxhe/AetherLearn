package com.aetherlearn.service.impl;

import com.aetherlearn.common.BusinessException;
import com.aetherlearn.common.JwtUtil;
import com.aetherlearn.common.RoleConstant;
import com.aetherlearn.dto.LoginRequest;
import com.aetherlearn.dto.LoginResponse;
import com.aetherlearn.dto.UserProfile;
import com.aetherlearn.entity.SysUser;
import com.aetherlearn.mapper.SysUserMapper;
import com.aetherlearn.service.AuthService;
import cn.hutool.crypto.SecureUtil;
import org.springframework.stereotype.Service;

/**
 * 认证服务实现（F-AUTH-01 登录接口）
 * <p>流程：查用户 → MD5 校验密码 → 生成 JWT → 组装响应。</p>
 */
@Service
public class AuthServiceImpl implements AuthService {

    private final SysUserMapper sysUserMapper;
    private final JwtUtil jwtUtil;

    public AuthServiceImpl(SysUserMapper sysUserMapper, JwtUtil jwtUtil) {
        this.sysUserMapper = sysUserMapper;
        this.jwtUtil = jwtUtil;
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        // 1. 按账号查询用户（Mapper 中已限定 status=1 的正常账号）
        SysUser user = sysUserMapper.selectByUsername(request.getUsername());
        if (user == null) {
            throw new BusinessException(401, "账号不存在或已被禁用");
        }

        // 2. 对明文密码做 MD5，与库中密文比对（初始口令 123456 → e10adc...）
        String md5 = SecureUtil.md5(request.getPassword());
        if (!md5.equals(user.getPassword())) {
            throw new BusinessException(401, "密码错误");
        }

        // 3. 签发 JWT（载荷含 userId / username / role）
        Long userId = user.getId();
        Integer role = user.getRole();
        String token = jwtUtil.generateToken(userId, user.getUsername(), role);

        // 4. 组装用户档案（不返回密码）
        UserProfile profile = new UserProfile();
        profile.setId(userId);
        profile.setUsername(user.getUsername());
        profile.setRealName(user.getRealName());
        profile.setRole(role);
        profile.setRoleName(RoleConstant.toRoleNameCn(role));
        profile.setAvatar(user.getAvatar());
        profile.setEmail(user.getEmail());
        profile.setPhone(user.getPhone());

        LoginResponse response = new LoginResponse();
        response.setToken(token);
        response.setUser(profile);
        return response;
    }
}
