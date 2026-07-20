package com.aetherlearn.service;

import com.aetherlearn.dto.CourseSaveRequest;
import com.aetherlearn.entity.Course;

import java.util.List;

/**
 * 课程服务接口（F-COURSE 课程管理模块）
 */
public interface CourseService {

    /**
     * 按角色列出课程（默认过滤软删除 is_deleted=0）
     * - 管理员：全部课程
     * - 教师：本人创建的课程
     * - 学生：已加入的课程
     */
    List<Course> listByRole(Long userId, Integer role);

    /**
     * 新建或编辑课程
     *
     * @param request    课程表单
     * @param operatorId 操作人ID
     * @param role       操作人角色
     * @return 保存后的课程
     */
    Course save(CourseSaveRequest request, Long operatorId, Integer role);

    /**
     * 删除课程（软删除，is_deleted 置 1）
     */
    void delete(Long id);

    /**
     * 生成课程加入邀请码（写入 course.invite_code）
     */
    String generateInviteCode(Long courseId);

    /**
     * 学生凭邀请码加入课程（写入 course_student）
     */
    void joinByInviteCode(Long studentId, String inviteCode);
}
