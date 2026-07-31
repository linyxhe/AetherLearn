package com.aetherlearn.service;

import com.aetherlearn.dto.UpdateUserRequest;
import com.aetherlearn.dto.UserAdminSaveRequest;
import com.aetherlearn.dto.UserProfile;
import com.aetherlearn.entity.SysUser;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

/**
 * 用户服务接口（F-AUTH-02 用户信息维护 / F-AUTH-03 用户管理）
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

    // ============ 管理员用户管理（F-AUTH-03） ============

    /**
     * 分页查询用户列表（支持关键字、角色筛选及安全字段排序）
     */
    Page<SysUser> listUsers(int page, int size, String keyword, Integer role, String sortBy, String sortOrder);

    /**
     * 查询用户详情
     */
    SysUser getUserById(Long id);

    /**
     * 创建用户（初始密码 123456，MD5 加密）
     */
    SysUser createUser(UserAdminSaveRequest request);

    /**
     * 编辑用户信息
     */
    SysUser updateUser(Long id, UserAdminSaveRequest request);

    /**
     * 删除用户（物理删除）
     */
    void deleteUser(Long id);

    /**
     * 启用/禁用用户
     */
    void updateUserStatus(Long id, Integer status);
}
