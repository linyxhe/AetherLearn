package com.aetherlearn.service.impl;

import com.aetherlearn.common.BusinessException;
import com.aetherlearn.common.RoleConstant;
import com.aetherlearn.dto.CourseNoticeSaveRequest;
import com.aetherlearn.entity.Course;
import com.aetherlearn.entity.CourseNotice;
import com.aetherlearn.mapper.CourseMapper;
import com.aetherlearn.mapper.CourseNoticeMapper;
import com.aetherlearn.service.CourseNoticeService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 课程公告服务实现（F-NOTIFY）
 */
@Service
public class CourseNoticeServiceImpl implements CourseNoticeService {

    private final CourseNoticeMapper courseNoticeMapper;
    private final CourseMapper courseMapper;

    public CourseNoticeServiceImpl(CourseNoticeMapper courseNoticeMapper, CourseMapper courseMapper) {
        this.courseNoticeMapper = courseNoticeMapper;
        this.courseMapper = courseMapper;
    }

    @Override
    public List<CourseNotice> listByRole(Long userId, Integer role, Long courseId) {
        if (role != null && role == 3) {
            List<CourseNotice> list = courseNoticeMapper.selectVisibleByStudent(userId);
            if (courseId == null) {
                return list;
            }
            return list.stream().filter(item -> courseId.equals(item.getCourseId())).collect(Collectors.toList());
        }
        LambdaQueryWrapper<CourseNotice> wrapper = new LambdaQueryWrapper<>();
        if (courseId != null) {
            Course selected = courseMapper.selectById(courseId);
            if (selected == null) {
                throw new BusinessException(404, "课程不存在");
            }
            // 教师只能查看本人课程公告，避免通过参数读取其他课程内容。
            if (RoleConstant.TEACHER == role && !java.util.Objects.equals(selected.getTeacherId(), userId)) {
                throw new BusinessException(403, "只能查看本人课程公告");
            }
            wrapper.eq(CourseNotice::getCourseId, courseId);
        } else {
            if (role != null && role == 2) {
                List<Course> courses = courseMapper.selectList(new LambdaQueryWrapper<Course>().eq(Course::getTeacherId, userId).eq(Course::getIsDeleted, 0));
                if (courses.isEmpty()) {
                    return Collections.emptyList();
                }
                wrapper.in(CourseNotice::getCourseId, courses.stream().map(Course::getId).collect(Collectors.toList()));
            }
        }
        wrapper.orderByDesc(CourseNotice::getCreateTime);
        return courseNoticeMapper.selectList(wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CourseNotice save(CourseNoticeSaveRequest request, Long operatorId, Integer role) {
        Course course = courseMapper.selectById(request.getCourseId());
        assertOperator(course, operatorId, role);
        CourseNotice notice = request.getId() == null
                ? new CourseNotice()
                : courseNoticeMapper.selectById(request.getId());
        if (notice == null) {
            throw new BusinessException(404, "公告不存在");
        }
        if (notice.getId() != null && !java.util.Objects.equals(notice.getCourseId(), course.getId())) {
            throw new BusinessException(400, "公告不属于指定课程");
        }
        notice.setCourseId(request.getCourseId());
        notice.setTitle(request.getTitle());
        notice.setContent(request.getContent());
        notice.setNoticeType(request.getNoticeType() == null ? "NOTICE" : request.getNoticeType());
        notice.setStatus(request.getStatus() == null ? 1 : request.getStatus());
        notice.setCreateBy(operatorId);
        if (notice.getId() == null) {
            notice.setCreateTime(LocalDateTime.now());
            notice.setUpdateTime(LocalDateTime.now());
            courseNoticeMapper.insert(notice);
        } else {
            notice.setUpdateTime(LocalDateTime.now());
            courseNoticeMapper.updateById(notice);
        }
        return notice;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id, Long operatorId, Integer role) {
        CourseNotice notice = courseNoticeMapper.selectById(id);
        if (notice == null) {
            throw new BusinessException(404, "公告不存在");
        }
        assertOperator(courseMapper.selectById(notice.getCourseId()), operatorId, role);
        courseNoticeMapper.deleteById(id);
    }

    /** 校验公告所属课程的维护权限，管理员可维护全部课程。 */
    private void assertOperator(Course course, Long operatorId, Integer role) {
        if (course == null) {
            throw new BusinessException(404, "课程不存在");
        }
        if (RoleConstant.ADMIN == role) {
            return;
        }
        if (RoleConstant.TEACHER != role || !java.util.Objects.equals(course.getTeacherId(), operatorId)) {
            throw new BusinessException(403, "只能操作本人课程公告");
        }
    }
}
