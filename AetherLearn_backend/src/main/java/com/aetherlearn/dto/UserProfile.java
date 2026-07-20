package com.aetherlearn.dto;

import lombok.Data;

/**
 * 用户档案（对外返回，隐藏密码）（F-AUTH 用户信息）
 */
@Data
public class UserProfile {
    private Long id;
    private String username;
    private String realName;
    private Integer role;
    private String roleName;
    private String avatar;
    private String email;
    private String phone;
}
