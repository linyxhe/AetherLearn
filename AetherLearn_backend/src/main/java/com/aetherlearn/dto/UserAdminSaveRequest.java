package com.aetherlearn.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 管理员创建/编辑用户请求（F-AUTH-03 用户管理）
 */
@Data
public class UserAdminSaveRequest {
    /** 登录账号（创建时必填） */
    @NotBlank(message = "请输入用户名")
    private String username;
    /** 真实姓名 */
    @NotBlank(message = "请输入姓名")
    private String realName;
    /** 角色：1-管理员 2-教师 3-学生（创建时必填） */
    @NotNull(message = "请选择角色")
    private Integer role;
    /** 邮箱 */
    private String email;
    /** 手机号 */
    private String phone;
    /** 密码（明文，创建时必填；编辑时可选，传入则修改） */
    private String password;
}
