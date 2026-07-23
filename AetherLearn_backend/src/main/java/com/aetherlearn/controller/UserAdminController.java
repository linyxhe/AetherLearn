package com.aetherlearn.controller;

import com.aetherlearn.common.Result;
import com.aetherlearn.dto.UserAdminSaveRequest;
import com.aetherlearn.entity.SysUser;
import com.aetherlearn.service.UserService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 管理员用户管理控制器（F-AUTH-03 用户管理增删改查）
 * <p>路径：/api/admin/users —— 仅管理员可访问。</p>
 */
@RestController
@RequestMapping("/api/admin/users")
public class UserAdminController {

    private final UserService userService;

    public UserAdminController(UserService userService) {
        this.userService = userService;
    }

    /**
     * 用户列表（分页 + 筛选）
     * @param page 页码（默认 1）
     * @param size 每页条数（默认 10）
     * @param keyword 搜索关键字（用户名/姓名）
     * @param role 角色筛选（1-管理员 2-教师 3-学生）
     */
    @GetMapping
    public Result<Map<String, Object>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer role) {
        Page<SysUser> result = userService.listUsers(page, size, keyword, role);
        Map<String, Object> data = new HashMap<>();
        data.put("records", result.getRecords());
        data.put("total", result.getTotal());
        data.put("pages", result.getPages());
        data.put("current", result.getCurrent());
        return Result.success(data);
    }

    /**
     * 用户详情
     */
    @GetMapping("/{id}")
    public Result<SysUser> detail(@PathVariable Long id) {
        return Result.success(userService.getUserById(id));
    }

    /**
     * 创建用户
     */
    @PostMapping
    public Result<SysUser> create(@Valid @RequestBody UserAdminSaveRequest request) {
        SysUser user = userService.createUser(request);
        return Result.success("创建成功", user);
    }

    /**
     * 编辑用户
     */
    @PutMapping("/{id}")
    public Result<SysUser> update(@PathVariable Long id, @Valid @RequestBody UserAdminSaveRequest request) {
        SysUser user = userService.updateUser(id, request);
        return Result.success("更新成功", user);
    }

    /**
     * 删除用户
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        userService.deleteUser(id);
        return Result.success("删除成功", null);
    }

    /**
     * 启用/禁用用户
     */
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        Integer status = body.get("status");
        if (status == null || (status != 0 && status != 1)) {
            return Result.error(400, "状态值无效，应为 0（禁用）或 1（启用）");
        }
        userService.updateUserStatus(id, status);
        return Result.success("状态更新成功", null);
    }
}
