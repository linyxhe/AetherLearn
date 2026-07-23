package com.aetherlearn.controller;

import com.aetherlearn.common.Result;
import com.aetherlearn.common.SecurityUtils;
import com.aetherlearn.dto.CourseNoticeSaveRequest;
import com.aetherlearn.entity.CourseNotice;
import com.aetherlearn.service.CourseNoticeService;
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
 * 课程公告控制器（F-NOTIFY）
 */
@RestController
@RequestMapping("/api/course/notices")
public class CourseNoticeController {

    private final CourseNoticeService courseNoticeService;

    public CourseNoticeController(CourseNoticeService courseNoticeService) {
        this.courseNoticeService = courseNoticeService;
    }

    /** 查询课程公告 */
    @GetMapping
    public Result<List<CourseNotice>> list(@RequestParam(required = false) Long courseId) {
        return Result.success(courseNoticeService.listByRole(SecurityUtils.getCurrentUserId(), SecurityUtils.getCurrentRole(), courseId));
    }

    /** 保存课程公告 */
    @PreAuthorize("hasRole('TEACHER')")
    @PostMapping
    public Result<CourseNotice> save(@Valid @RequestBody CourseNoticeSaveRequest request) {
        return Result.success("保存成功", courseNoticeService.save(request, SecurityUtils.getCurrentUserId()));
    }

    /** 删除课程公告 */
    @PreAuthorize("hasRole('TEACHER')")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        courseNoticeService.delete(id);
        return Result.success();
    }
}

