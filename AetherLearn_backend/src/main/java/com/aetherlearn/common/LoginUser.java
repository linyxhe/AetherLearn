package com.aetherlearn.common;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 登录用户身份载体（F-AUTH / 权限拦截）
 * <p>作为 Spring Security 的 Authentication 主体（principal），供 Controller/Service/工具类随时取用。</p>
 */
@Data
@AllArgsConstructor
public class LoginUser {

    /** 用户ID */
    private Long userId;

    /** 登录账号 */
    private String username;

    /** 角色编码（1/2/3） */
    private Integer role;

    /** 角色名（ADMIN/TEACHER/STUDENT），便于前端直接展示 */
    private String roleName;

    public LoginUser(Long userId, String username, Integer role) {
        this.userId = userId;
        this.username = username;
        this.role = role;
        this.roleName = RoleConstant.toRoleName(role);
    }
}
