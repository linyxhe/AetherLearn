package com.aetherlearn.service.impl;

import com.aetherlearn.common.BusinessException;
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
    public CourseNotice save(CourseNoticeSaveRequest request, Long operatorId) {
        CourseNotice notice = request.getId() == null
                ? new CourseNotice()
                : courseNoticeMapper.selectById(request.getId());
        if (notice == null) {
            throw new BusinessException(404, "公告不存在");
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
    public void delete(Long id) {
        courseNoticeMapper.deleteById(id);
    }
}
