package com.aetherlearn.common;

/**
 * 角色常量（F-AUTH / 权限拦截）
 * <p>统一管理三角色编码与 Spring Security 所需的权限名（ROLE_ 前缀）。</p>
 */
public final class RoleConstant {

    /** 角色编码 */
    public static final int ADMIN = 1;
    public static final int TEACHER = 2;
    public static final int STUDENT = 3;

    /** 角色中文名 */
    public static final String ADMIN_NAME = "ADMIN";
    public static final String TEACHER_NAME = "TEACHER";
    public static final String STUDENT_NAME = "STUDENT";

    private RoleConstant() {
    }

    /** 由数字角色编码得到 Spring Security 权限名（如 "ROLE_ADMIN"） */
    public static String toAuthority(Integer role) {
        return switch (role) {
            case ADMIN -> "ROLE_" + ADMIN_NAME;
            case TEACHER -> "ROLE_" + TEACHER_NAME;
            case STUDENT -> "ROLE_" + STUDENT_NAME;
            default -> "ROLE_UNKNOWN";
        };
    }

    /** 由数字角色编码得到角色名（如 "TEACHER"） */
    public static String toRoleName(Integer role) {
        return switch (role) {
            case ADMIN -> ADMIN_NAME;
            case TEACHER -> TEACHER_NAME;
            case STUDENT -> STUDENT_NAME;
            default -> "UNKNOWN";
        };
    }
}
