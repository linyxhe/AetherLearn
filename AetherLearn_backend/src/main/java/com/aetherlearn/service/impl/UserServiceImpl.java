package com.aetherlearn.service.impl;

import com.aetherlearn.common.BusinessException;
import com.aetherlearn.common.RoleConstant;
import com.aetherlearn.dto.UpdateUserRequest;
import com.aetherlearn.dto.UserProfile;
import com.aetherlearn.entity.SysUser;
import com.aetherlearn.mapper.SysUserMapper;
import com.aetherlearn.service.UserService;
import cn.hutool.crypto.SecureUtil;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

/**
 * 用户服务实现（F-AUTH-02 用户信息维护）
 * <p>提供当前用户档案查询与信息更新（含可选改密，密码 MD5 存储）。</p>
 */
@Service
public class UserServiceImpl implements UserService {

    private final SysUserMapper sysUserMapper;

    public UserServiceImpl(SysUserMapper sysUserMapper) {
        this.sysUserMapper = sysUserMapper;
    }

    @Override
    public UserProfile getInfo(Long userId) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        return toProfile(user);
    }

    @Override
    public UserProfile update(Long userId, UpdateUserRequest request) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        // 仅更新允许修改的字段
        if (request.getRealName() != null) user.setRealName(request.getRealName());
        if (request.getAvatar() != null) user.setAvatar(request.getAvatar());
        if (request.getEmail() != null) user.setEmail(request.getEmail());
        if (request.getPhone() != null) user.setPhone(request.getPhone());
        // 可选改密：传入 password 则做 MD5 后更新
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            user.setPassword(SecureUtil.md5(request.getPassword()));
        }
        sysUserMapper.updateById(user);
        return toProfile(user);
    }

    /** 实体 → 对外档案（隐藏密码） */
    private UserProfile toProfile(SysUser user) {
        UserProfile profile = new UserProfile();
        BeanUtils.copyProperties(user, profile);
        profile.setRoleName(RoleConstant.toRoleName(user.getRole()));
        return profile;
    }
}
