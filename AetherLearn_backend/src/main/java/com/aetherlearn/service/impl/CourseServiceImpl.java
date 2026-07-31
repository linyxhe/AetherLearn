package com.aetherlearn.service.impl;

import com.aetherlearn.common.BusinessException;
import com.aetherlearn.common.RoleConstant;
import com.aetherlearn.dto.CourseChapterSaveRequest;
import com.aetherlearn.dto.CourseSaveRequest;
import com.aetherlearn.entity.Course;
import com.aetherlearn.entity.CourseChapter;
import com.aetherlearn.entity.CourseStudent;
import com.aetherlearn.entity.LearningRecord;
import com.aetherlearn.entity.SysUser;
import com.aetherlearn.mapper.CourseChapterMapper;
import com.aetherlearn.mapper.CourseMapper;
import com.aetherlearn.mapper.CourseStudentMapper;
import com.aetherlearn.mapper.LearningRecordMapper;
import com.aetherlearn.service.CourseService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 课程服务实现（F-COURSE 课程管理模块）
 * <p>实现课程增删改查（查询默认过滤软删除），以及邀请码生成与学生加入。</p>
 */
@Service
public class CourseServiceImpl implements CourseService {

    private final CourseMapper courseMapper;
    private final CourseStudentMapper courseStudentMapper;
    private final CourseChapterMapper courseChapterMapper;
    private final LearningRecordMapper learningRecordMapper;

    /** 章节文件上传根目录。 */
    @Value("${file.upload-dir}")
    private String uploadDir;

    public CourseServiceImpl(CourseMapper courseMapper,
                             CourseStudentMapper courseStudentMapper,
                             CourseChapterMapper courseChapterMapper,
                             LearningRecordMapper learningRecordMapper) {
        this.courseMapper = courseMapper;
        this.courseStudentMapper = courseStudentMapper;
        this.courseChapterMapper = courseChapterMapper;
        this.learningRecordMapper = learningRecordMapper;
    }

    @Override
    public List<Course> listByRole(Long userId, Integer role) {
        // 学生：返回已加入课程（自定义 JOIN 查询，已内部过滤软删除）
        if (role != null && role == RoleConstant.STUDENT) {
            return courseMapper.selectByStudentId(userId);
        }
        // 教师：本人课程；管理员：全部课程
        LambdaQueryWrapper<Course> wrapper = new LambdaQueryWrapper<>();
        if (role != null && role == RoleConstant.TEACHER) {
            wrapper.eq(Course::getTeacherId, userId);
        }
        wrapper.orderByDesc(Course::getCreateTime);
        // MyBatis-Plus 全局逻辑删除会自动附加 is_deleted = 0
        return courseMapper.selectList(wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Course save(CourseSaveRequest request, Long operatorId, Integer role) {
        Course course;
        if (request.getId() != null) {
            // 编辑：先查是否存在（未软删除）
            course = courseMapper.selectById(request.getId());
            if (course == null) {
                throw new BusinessException(404, "课程不存在");
            }
            course.setCourseName(request.getCourseName());
            course.setCourseCode(request.getCourseCode());
            course.setDescription(request.getDescription());
            course.setCover(request.getCover());
            if (request.getStatus() != null) {
                course.setStatus(request.getStatus());
            }
            courseMapper.updateById(course);
            return course;
        }
        // 新建
        Course newCourse = new Course();
        // 教师创建的课程归属本人；管理员可建课程（teacherId 置为操作人，演示用）
        newCourse.setTeacherId(operatorId);
        newCourse.setCourseName(request.getCourseName());
        newCourse.setCourseCode(request.getCourseCode());
        newCourse.setDescription(request.getDescription());
        newCourse.setCover(request.getCover());
        newCourse.setStatus(request.getStatus() != null ? request.getStatus() : 1);
        newCourse.setCreateTime(LocalDateTime.now());
        courseMapper.insert(newCourse);
        return newCourse;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        // removeById 配合 @TableLogic 会执行软删除（UPDATE is_deleted = 1）
        int rows = courseMapper.deleteById(id);
        if (rows == 0) {
            throw new BusinessException(404, "课程不存在或已删除");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String generateInviteCode(Long courseId) {
        Course course = courseMapper.selectById(courseId);
        if (course == null) {
            throw new BusinessException(404, "课程不存在");
        }
        // 生成 6 位随机邀请码，若碰撞则重新生成（最多重试 5 次）
        String code = null;
        for (int i = 0; i < 5; i++) {
            String candidate = UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
            LambdaQueryWrapper<Course> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(Course::getInviteCode, candidate);
            Long count = courseMapper.selectCount(wrapper);
            if (count == 0) {
                code = candidate;
                break;
            }
        }
        if (code == null) {
            throw new BusinessException(500, "邀请码生成失败，请重试");
        }
        course.setInviteCode(code);
        courseMapper.updateById(course);
        return code;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void joinByInviteCode(Long studentId, String inviteCode) {
        if (inviteCode == null || inviteCode.isBlank()) {
            throw new BusinessException(400, "邀请码不能为空");
        }
        LambdaQueryWrapper<Course> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Course::getInviteCode, inviteCode);
        Course course = courseMapper.selectOne(wrapper);
        if (course == null) {
            throw new BusinessException(400, "邀请码无效");
        }
        // 重复加入校验
        int existed = courseStudentMapper.countByCourseAndStudent(course.getId(), studentId);
        if (existed > 0) {
            throw new BusinessException(400, "你已加入该课程");
        }
        CourseStudent cs = new CourseStudent();
        cs.setCourseId(course.getId());
        cs.setStudentId(studentId);
        cs.setCreateTime(LocalDateTime.now());
        courseStudentMapper.insert(cs);
    }

    @Override
    public List<SysUser> listStudents(Long courseId) {
        // 校验课程是否存在
        Course course = courseMapper.selectById(courseId);
        if (course == null) {
            throw new BusinessException(404, "课程不存在");
        }
        return courseStudentMapper.selectStudentsByCourseId(courseId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeStudent(Long courseId, Long studentId) {
        // 校验课程是否存在
        Course course = courseMapper.selectById(courseId);
        if (course == null) {
            throw new BusinessException(404, "课程不存在");
        }
        // 校验学生是否在该课程中
        int existed = courseStudentMapper.countByCourseAndStudent(courseId, studentId);
        if (existed == 0) {
            throw new BusinessException(400, "该学生未加入此课程");
        }
        courseStudentMapper.deleteByCourseAndStudent(courseId, studentId);
    }

    @Override
    public List<CourseChapter> listChapters(Long courseId, Long studentId, Integer role) {
        Course course = courseMapper.selectById(courseId);
        if (course == null) {
            throw new BusinessException(404, "课程不存在");
        }
        LambdaQueryWrapper<CourseChapter> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CourseChapter::getCourseId, courseId);
        if (role != null && role == RoleConstant.STUDENT) {
            wrapper.eq(CourseChapter::getStatus, 1);
        }
        wrapper.orderByAsc(CourseChapter::getSortNo).orderByAsc(CourseChapter::getId);
        List<CourseChapter> chapters = courseChapterMapper.selectList(wrapper);
        if (role != null && role == RoleConstant.STUDENT && studentId != null) {
            chapters.forEach(chapter ->
                    chapter.setCompleted(learningRecordMapper.countChapterRecord(studentId, chapter.getId()) > 0));
        }
        return chapters;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CourseChapter saveChapter(CourseChapterSaveRequest request) {
        Course course = courseMapper.selectById(request.getCourseId());
        if (course == null) {
            throw new BusinessException(404, "课程不存在");
        }
        CourseChapter chapter = request.getId() == null ? new CourseChapter() : courseChapterMapper.selectById(request.getId());
        if (chapter == null) {
            throw new BusinessException(404, "章节不存在");
        }
        chapter.setCourseId(request.getCourseId());
        chapter.setTitle(request.getTitle());
        chapter.setContent(request.getContent());
        chapter.setResourceType(request.getResourceType() == null ? "TEXT" : request.getResourceType());
        // 编辑时未重新上传文件不应覆盖已有资源路径，避免章节资料在保存后丢失。
        String resourceUrl = request.getResourceUrl();
        if (chapter.getId() != null && (resourceUrl == null || resourceUrl.isBlank())) {
            resourceUrl = chapter.getResourceUrl();
        }
        chapter.setResourceUrl(resourceUrl);
        chapter.setDurationMinutes(request.getDurationMinutes() == null ? 15 : request.getDurationMinutes());
        chapter.setSortNo(request.getSortNo() == null ? 1 : request.getSortNo());
        chapter.setStatus(request.getStatus() == null ? 1 : request.getStatus());
        chapter.setUpdateTime(LocalDateTime.now());
        if (chapter.getId() == null) {
            chapter.setCreateTime(LocalDateTime.now());
            courseChapterMapper.insert(chapter);
        } else {
            courseChapterMapper.updateById(chapter);
        }
        return chapter;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteChapter(Long chapterId) {
        int rows = courseChapterMapper.deleteById(chapterId);
        if (rows == 0) {
            throw new BusinessException(404, "章节不存在");
        }
    }

    /**
     * 删除章节资源文件，并将数据库中的资源地址清空。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteChapterResource(Long chapterId) {
        CourseChapter chapter = courseChapterMapper.selectById(chapterId);
        if (chapter == null) {
            throw new BusinessException(404, "章节不存在");
        }
        deleteStoredCourseResource(chapter.getResourceUrl());
        courseChapterMapper.update(null, new LambdaUpdateWrapper<CourseChapter>()
                .eq(CourseChapter::getId, chapterId)
                .set(CourseChapter::getResourceUrl, null)
                .set(CourseChapter::getUpdateTime, LocalDateTime.now()));
    }

    /**
     * 仅允许删除 uploads/course 分桶中的文件，防止通过资源地址越界删除其他文件。
     */
    private void deleteStoredCourseResource(String resourceUrl) {
        if (resourceUrl == null || resourceUrl.isBlank()) {
            return;
        }
        final String prefix = "/uploads/course/";
        if (!resourceUrl.startsWith(prefix)) {
            return;
        }
        Path courseDir = Paths.get(uploadDir, "course").toAbsolutePath().normalize();
        Path target = courseDir.resolve(resourceUrl.substring(prefix.length())).normalize();
        if (!target.startsWith(courseDir)) {
            throw new BusinessException(400, "非法的章节资源路径");
        }
        try {
            Files.deleteIfExists(target);
        } catch (IOException e) {
            throw new BusinessException(500, "删除章节资源文件失败：" + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void completeChapter(Long chapterId, Long studentId) {
        CourseChapter chapter = courseChapterMapper.selectById(chapterId);
        if (chapter == null || chapter.getStatus() == null || chapter.getStatus() != 1) {
            throw new BusinessException(404, "章节不存在或未发布");
        }
        if (courseStudentMapper.countByCourseAndStudent(chapter.getCourseId(), studentId) == 0) {
            throw new BusinessException(403, "请先加入课程再学习");
        }
        if (learningRecordMapper.countChapterRecord(studentId, chapterId) > 0) {
            return;
        }
        LearningRecord record = new LearningRecord();
        record.setStudentId(studentId);
        record.setCourseId(chapter.getCourseId());
        record.setActionType("章节学习");
        record.setTargetId(chapterId);
        record.setDuration((chapter.getDurationMinutes() == null ? 15 : chapter.getDurationMinutes()) * 60);
        record.setCreateTime(LocalDateTime.now());
        learningRecordMapper.insert(record);
    }
}
