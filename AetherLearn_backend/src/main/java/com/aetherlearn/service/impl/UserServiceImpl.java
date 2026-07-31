package com.aetherlearn.service.impl;

import com.aetherlearn.common.BusinessException;
import com.aetherlearn.common.RoleConstant;
import com.aetherlearn.dto.UpdateUserRequest;
import com.aetherlearn.dto.UserAdminSaveRequest;
import com.aetherlearn.dto.UserProfile;
import com.aetherlearn.entity.SysUser;
import com.aetherlearn.mapper.SysUserMapper;
import com.aetherlearn.service.UserService;
import cn.hutool.crypto.SecureUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.regex.Pattern;

/**
 * 用户服务实现（F-AUTH-02 用户信息维护 / F-AUTH-03 用户管理）
 * <p>提供当前用户档案查询、信息更新、管理员 CRUD 操作。</p>
 */
@Service
public class UserServiceImpl implements UserService {

    /** 初始默认密码 */
    private static final String DEFAULT_PASSWORD = "123456";

    /** 邮箱正则 */
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[\\w.-]+@[\\w.-]+\\.[a-zA-Z]{2,}$");
    /** 手机号正则（11 位数字） */
    private static final Pattern PHONE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");

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
    @Transactional(rollbackFor = Exception.class)
    public UserProfile update(Long userId, UpdateUserRequest request) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        // 仅更新允许修改的字段，带格式校验
        if (request.getRealName() != null) user.setRealName(request.getRealName());
        if (request.getAvatar() != null) user.setAvatar(request.getAvatar());
        if (request.getEmail() != null) {
            if (!request.getEmail().isBlank() && !EMAIL_PATTERN.matcher(request.getEmail()).matches()) {
                throw new BusinessException(400, "邮箱格式不正确");
            }
            user.setEmail(request.getEmail());
        }
        if (request.getPhone() != null) {
            if (!request.getPhone().isBlank() && !PHONE_PATTERN.matcher(request.getPhone()).matches()) {
                throw new BusinessException(400, "手机号格式不正确（需为 11 位有效手机号）");
            }
            user.setPhone(request.getPhone());
        }
        // 可选改密：传入 password 则做 MD5 后更新（最少 6 位）
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            if (request.getPassword().length() < 6) {
                throw new BusinessException(400, "密码长度不能少于 6 位");
            }
            user.setPassword(SecureUtil.md5(request.getPassword()));
        }
        user.setUpdateTime(LocalDateTime.now());
        sysUserMapper.updateById(user);
        return toProfile(user);
    }

    // ============ 管理员用户管理（F-AUTH-03） ============

    @Override
    public Page<SysUser> listUsers(int page, int size, String keyword, Integer role,
                                   String sortBy, String sortOrder) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        // 关键字搜索（用户名或姓名）
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w
                    .like(SysUser::getUsername, keyword)
                    .or()
                    .like(SysUser::getRealName, keyword)
            );
        }
        // 角色筛选
        if (role != null) {
            wrapper.eq(SysUser::getRole, role);
        }
        // 仅允许预定义字段参与排序，避免将前端参数直接拼接进 SQL。
        boolean ascending = "asc".equalsIgnoreCase(sortOrder);
        switch (sortBy == null ? "createTime" : sortBy) {
            case "username" -> wrapper.orderBy(true, ascending, SysUser::getUsername);
            case "realName" -> wrapper.orderBy(true, ascending, SysUser::getRealName);
            case "role" -> wrapper.orderBy(true, ascending, SysUser::getRole);
            case "status" -> wrapper.orderBy(true, ascending, SysUser::getStatus);
            case "createTime" -> wrapper.orderBy(true, ascending, SysUser::getCreateTime);
            default -> wrapper.orderByDesc(SysUser::getCreateTime);
        }
        wrapper.orderByDesc(SysUser::getId);
        return sysUserMapper.selectPage(new Page<>(page, size), wrapper);
    }

    @Override
    public SysUser getUserById(Long id) {
        SysUser user = sysUserMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        return user;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SysUser createUser(UserAdminSaveRequest request) {
        // 检查用户名唯一性
        SysUser existing = sysUserMapper.selectByUsername(request.getUsername());
        if (existing != null) {
            throw new BusinessException(400, "用户名已存在");
        }
        // 邮箱/手机号格式校验
        if (request.getEmail() != null && !request.getEmail().isBlank()
                && !EMAIL_PATTERN.matcher(request.getEmail()).matches()) {
            throw new BusinessException(400, "邮箱格式不正确");
        }
        if (request.getPhone() != null && !request.getPhone().isBlank()
                && !PHONE_PATTERN.matcher(request.getPhone()).matches()) {
            throw new BusinessException(400, "手机号格式不正确（需为 11 位有效手机号）");
        }
        SysUser user = new SysUser();
        user.setUsername(request.getUsername());
        user.setRealName(request.getRealName());
        user.setRole(request.getRole());
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());
        // 密码：使用传入的或默认密码，MD5 加密
        String pwd = (request.getPassword() != null && !request.getPassword().isBlank())
                ? request.getPassword() : DEFAULT_PASSWORD;
        user.setPassword(SecureUtil.md5(pwd));
        user.setStatus(1);
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        sysUserMapper.insert(user);
        return user;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SysUser updateUser(Long id, UserAdminSaveRequest request) {
        SysUser user = sysUserMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        // 如果修改了用户名，检查唯一性
        if (request.getUsername() != null && !request.getUsername().equals(user.getUsername())) {
            SysUser existing = sysUserMapper.selectByUsername(request.getUsername());
            if (existing != null) {
                throw new BusinessException(400, "用户名已存在");
            }
            user.setUsername(request.getUsername());
        }
        if (request.getRealName() != null) user.setRealName(request.getRealName());
        if (request.getRole() != null) user.setRole(request.getRole());
        if (request.getEmail() != null) {
            if (!request.getEmail().isBlank() && !EMAIL_PATTERN.matcher(request.getEmail()).matches()) {
                throw new BusinessException(400, "邮箱格式不正确");
            }
            user.setEmail(request.getEmail());
        }
        if (request.getPhone() != null) {
            if (!request.getPhone().isBlank() && !PHONE_PATTERN.matcher(request.getPhone()).matches()) {
                throw new BusinessException(400, "手机号格式不正确（需为 11 位有效手机号）");
            }
            user.setPhone(request.getPhone());
        }
        // 可选改密（最少 6 位）
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            if (request.getPassword().length() < 6) {
                throw new BusinessException(400, "密码长度不能少于 6 位");
            }
            user.setPassword(SecureUtil.md5(request.getPassword()));
        }
        user.setUpdateTime(LocalDateTime.now());
        sysUserMapper.updateById(user);
        return user;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteUser(Long id) {
        SysUser user = sysUserMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        sysUserMapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateUserStatus(Long id, Integer status) {
        SysUser user = sysUserMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        user.setStatus(status);
        user.setUpdateTime(LocalDateTime.now());
        sysUserMapper.updateById(user);
    }

    /** 实体 → 对外档案（隐藏密码） */
    private UserProfile toProfile(SysUser user) {
        UserProfile profile = new UserProfile();
        BeanUtils.copyProperties(user, profile);
        profile.setRoleName(RoleConstant.toRoleName(user.getRole()));
        return profile;
    }
}
