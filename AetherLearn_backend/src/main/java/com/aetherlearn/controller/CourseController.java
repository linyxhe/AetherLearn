package com.aetherlearn.controller;

import com.aetherlearn.common.Result;
import com.aetherlearn.common.SecurityUtils;
import com.aetherlearn.dto.CourseSaveRequest;
import com.aetherlearn.entity.Course;
import com.aetherlearn.service.CourseService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 课程控制器（F-COURSE 课程管理模块）
 * <p>路径：/api/course/** 。创建/编辑/删除限定 教师/管理员；学生加入限定 学生。</p>
 */
@RestController
@RequestMapping("/api/course")
public class CourseController {

    private final CourseService courseService;

    public CourseController(CourseService courseService) {
        this.courseService = courseService;
    }

    /**
     * 课程列表：按角色区分（教师/管理员看管辖课程，学生看已加入课程）
     */
    @GetMapping("/list")
    public Result<List<Course>> list() {
        Long userId = SecurityUtils.getCurrentUserId();
        Integer role = SecurityUtils.getCurrentRole();
        return Result.success(courseService.listByRole(userId, role));
    }

    /**
     * 新建/编辑课程（仅 教师/管理员）
     */
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @PostMapping
    public Result<Course> save(@Valid @RequestBody CourseSaveRequest request) {
        Long operatorId = SecurityUtils.getCurrentUserId();
        Integer role = SecurityUtils.getCurrentRole();
        return Result.success("保存成功", courseService.save(request, operatorId, role));
    }

    /**
     * 删除课程（软删除，仅 教师/管理员）
     */
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        courseService.delete(id);
        return Result.success();
    }

    /**
     * 生成课程邀请码（仅 教师/管理员）
     */
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @PostMapping("/{id}/invite")
    public Result<String> invite(@PathVariable Long id) {
        return Result.success("邀请码已生成", courseService.generateInviteCode(id));
    }

    /**
     * 学生凭邀请码加入课程
     */
    @PreAuthorize("hasRole('STUDENT')")
    @PostMapping("/join")
    public Result<Void> join(@RequestParam String code) {
        Long studentId = SecurityUtils.getCurrentUserId();
        courseService.joinByInviteCode(studentId, code);
        return Result.success();
    }
}
