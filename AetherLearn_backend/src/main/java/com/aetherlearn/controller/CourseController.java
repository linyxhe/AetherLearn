package com.aetherlearn.controller;

import com.aetherlearn.common.Result;
import com.aetherlearn.common.SecurityUtils;
import com.aetherlearn.dto.CourseChapterSaveRequest;
import com.aetherlearn.dto.CourseSaveRequest;
import com.aetherlearn.entity.Course;
import com.aetherlearn.entity.CourseChapter;
import com.aetherlearn.entity.SysUser;
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
    @PreAuthorize("hasRole('TEACHER')")
    @PostMapping
    public Result<Course> save(@Valid @RequestBody CourseSaveRequest request) {
        Long operatorId = SecurityUtils.getCurrentUserId();
        Integer role = SecurityUtils.getCurrentRole();
        return Result.success("保存成功", courseService.save(request, operatorId, role));
    }

    /**
     * 删除课程（软删除，仅 教师/管理员）
     */
    @PreAuthorize("hasRole('TEACHER')")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        courseService.delete(id);
        return Result.success();
    }

    /**
     * 生成课程邀请码（仅 教师/管理员）
     */
    @PreAuthorize("hasRole('TEACHER')")
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

    /**
     * 查询课程学生名单（F-COURSE-03，仅 教师/管理员）
     */
    @PreAuthorize("hasRole('TEACHER')")
    @GetMapping("/{id}/students")
    public Result<List<SysUser>> listStudents(@PathVariable Long id) {
        return Result.success(courseService.listStudents(id));
    }

    /**
     * 移除课程学生（F-COURSE-03，仅 教师/管理员）
     */
    @PreAuthorize("hasRole('TEACHER')")
    @DeleteMapping("/{id}/students/{studentId}")
    public Result<Void> removeStudent(@PathVariable Long id, @PathVariable Long studentId) {
        courseService.removeStudent(id, studentId);
        return Result.success();
    }

    /**
     * 查询课程在线学习章节（教师维护、学生学习）
     */
    @GetMapping("/{id}/chapters")
    public Result<List<CourseChapter>> listChapters(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        Integer role = SecurityUtils.getCurrentRole();
        return Result.success(courseService.listChapters(id, userId, role));
    }

    /**
     * 新增/编辑在线学习章节（仅教师/管理员）
     */
    @PreAuthorize("hasRole('TEACHER')")
    @PostMapping("/chapters")
    public Result<CourseChapter> saveChapter(@Valid @RequestBody CourseChapterSaveRequest request) {
        return Result.success("章节保存成功", courseService.saveChapter(request));
    }

    /**
     * 删除在线学习章节（仅教师/管理员）
     */
    @PreAuthorize("hasRole('TEACHER')")
    @DeleteMapping("/chapters/{chapterId}")
    public Result<Void> deleteChapter(@PathVariable Long chapterId) {
        courseService.deleteChapter(chapterId);
        return Result.success();
    }

    /**
     * 删除章节已上传的资源文件（仅教师/管理员）。
     */
    @PreAuthorize("hasRole('TEACHER')")
    @DeleteMapping("/chapters/{chapterId}/resource")
    public Result<Void> deleteChapterResource(@PathVariable Long chapterId) {
        courseService.deleteChapterResource(chapterId);
        return Result.success();
    }

    /**
     * 学生完成章节学习，写入学习进度。
     */
    @PreAuthorize("hasRole('STUDENT')")
    @PostMapping("/chapters/{chapterId}/complete")
    public Result<Void> completeChapter(@PathVariable Long chapterId) {
        courseService.completeChapter(chapterId, SecurityUtils.getCurrentUserId());
        return Result.success();
    }
}

