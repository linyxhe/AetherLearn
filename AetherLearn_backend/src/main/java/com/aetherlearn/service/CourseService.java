package com.aetherlearn.service;

import com.aetherlearn.dto.CourseSaveRequest;
import com.aetherlearn.dto.CourseChapterSaveRequest;
import com.aetherlearn.entity.Course;
import com.aetherlearn.entity.CourseChapter;
import com.aetherlearn.entity.SysUser;

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
    void delete(Long id, Long operatorId, Integer role);

    /**
     * 生成课程加入邀请码（写入 course.invite_code）
     */
    String generateInviteCode(Long courseId, Long operatorId, Integer role);

    /**
     * 学生凭邀请码加入课程（写入 course_student）
     */
    void joinByInviteCode(Long studentId, String inviteCode);

    /**
     * 查询某课程下所有学生（F-COURSE-03 学生名单）
     */
    List<SysUser> listStudents(Long courseId, Long operatorId, Integer role);

    /**
     * 教师移除课程中的某学生（F-COURSE-03 学生移除）
     */
    void removeStudent(Long courseId, Long studentId, Long operatorId, Integer role);

    /**
     * 查询课程在线学习章节；学生仅查看已发布章节，并带完成状态。
     */
    List<CourseChapter> listChapters(Long courseId, Long studentId, Integer role);

    /**
     * 教师新增或编辑课程在线学习章节。
     */
    CourseChapter saveChapter(CourseChapterSaveRequest request, Long operatorId, Integer role);

    /**
     * 教师删除课程在线学习章节。
     */
    void deleteChapter(Long chapterId, Long operatorId, Integer role);

    /**
     * 删除章节已上传的资源文件，并清空章节的资源地址。
     */
    void deleteChapterResource(Long chapterId, Long operatorId, Integer role);

    /**
     * 学生完成章节学习，并写入学习行为记录。
     */
    void completeChapter(Long chapterId, Long studentId);
}
