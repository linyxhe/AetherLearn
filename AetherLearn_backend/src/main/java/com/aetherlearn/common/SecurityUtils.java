package com.aetherlearn.common;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 安全工具类（F-AUTH / 权限拦截）
 * <p>从 Spring Security 上下文取出当前登录用户 {@link LoginUser}。</p>
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    /**
     * 获取当前登录用户（未登录返回 null）
     */
    public static LoginUser getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof LoginUser loginUser) {
            return loginUser;
        }
        return null;
    }

    /** 获取当前登录用户ID */
    public static Long getCurrentUserId() {
        LoginUser user = getCurrentUser();
        return user == null ? null : user.getUserId();
    }

    /** 获取当前角色编码 */
    public static Integer getCurrentRole() {
        LoginUser user = getCurrentUser();
        return user == null ? null : user.getRole();
    }
}
