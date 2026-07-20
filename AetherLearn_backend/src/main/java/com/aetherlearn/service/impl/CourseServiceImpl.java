package com.aetherlearn.service.impl;

import com.aetherlearn.common.BusinessException;
import com.aetherlearn.common.RoleConstant;
import com.aetherlearn.dto.CourseSaveRequest;
import com.aetherlearn.entity.Course;
import com.aetherlearn.entity.CourseStudent;
import com.aetherlearn.mapper.CourseMapper;
import com.aetherlearn.mapper.CourseStudentMapper;
import com.aetherlearn.service.CourseService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    public CourseServiceImpl(CourseMapper courseMapper, CourseStudentMapper courseStudentMapper) {
        this.courseMapper = courseMapper;
        this.courseStudentMapper = courseStudentMapper;
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
        // 生成 6 位随机邀请码
        String code = UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
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
}
