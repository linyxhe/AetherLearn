package com.aetherlearn.dto;

import lombok.Data;

/**
 * 用户信息更新请求（F-AUTH-02 用户信息维护）
 * <p>允许修改昵称、头像、邮箱、手机号；password 可选（传入则修改密码）。</p>
 */
@Data
public class UpdateUserRequest {
    /** 真实姓名/昵称 */
    private String realName;
    /** 头像路径（uploads/avatar） */
    private String avatar;
    /** 邮箱 */
    private String email;
    /** 手机号 */
    private String phone;
    /** 新密码（明文，可选；传入则做 MD5 后更新） */
    private String password;
}
