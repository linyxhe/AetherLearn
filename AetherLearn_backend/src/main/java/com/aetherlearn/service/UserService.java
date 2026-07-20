package com.aetherlearn.service;

import com.aetherlearn.dto.UpdateUserRequest;
import com.aetherlearn.dto.UserProfile;

/**
 * 用户服务接口（F-AUTH-02 用户信息维护）
 */
public interface UserService {

    /**
     * 查询当前登录用户档案
     */
    UserProfile getInfo(Long userId);

    /**
     * 更新当前登录用户信息（昵称/头像/邮箱/手机号/可选改密）
     */
    UserProfile update(Long userId, UpdateUserRequest request);
}
