package com.aetherlearn.service.impl;

import com.aetherlearn.common.BusinessException;
import com.aetherlearn.dto.LearningTodoSaveRequest;
import com.aetherlearn.entity.Course;
import com.aetherlearn.entity.LearningTodo;
import com.aetherlearn.mapper.CourseMapper;
import com.aetherlearn.mapper.LearningTodoMapper;
import com.aetherlearn.service.LearningTodoService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

/**
 * 学习计划/待办服务实现
 */
@Service
public class LearningTodoServiceImpl implements LearningTodoService {

    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final LearningTodoMapper learningTodoMapper;
    private final CourseMapper courseMapper;

    public LearningTodoServiceImpl(LearningTodoMapper learningTodoMapper, CourseMapper courseMapper) {
        this.learningTodoMapper = learningTodoMapper;
        this.courseMapper = courseMapper;
    }

    @Override
    public List<LearningTodo> listMine(Long userId, Integer status, Long courseId) {
        LambdaQueryWrapper<LearningTodo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LearningTodo::getUserId, userId);
        if (status != null) {
            wrapper.eq(LearningTodo::getStatus, status);
        }
        if (courseId != null) {
            wrapper.eq(LearningTodo::getCourseId, courseId);
        }
        wrapper.orderByAsc(LearningTodo::getStatus)
                .orderByAsc(LearningTodo::getDueTime)
                .orderByDesc(LearningTodo::getPriority)
                .orderByDesc(LearningTodo::getCreateTime);
        List<LearningTodo> list = learningTodoMapper.selectList(wrapper);
        list.forEach(this::fillCourseName);
        return list;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LearningTodo save(Long userId, LearningTodoSaveRequest request) {
        LearningTodo todo = request.getId() == null ? new LearningTodo() : learningTodoMapper.selectById(request.getId());
        if (todo == null) {
            throw new BusinessException(404, "待办不存在");
        }
        if (todo.getId() != null && !Objects.equals(todo.getUserId(), userId)) {
            throw new BusinessException(403, "只能编辑自己的待办");
        }
        todo.setUserId(userId);
        todo.setCourseId(request.getCourseId());
        todo.setTitle(request.getTitle());
        todo.setContent(request.getContent());
        todo.setTodoType(request.getTodoType() == null ? "PLAN" : request.getTodoType());
        todo.setPriority(request.getPriority() == null ? 2 : request.getPriority());
        todo.setDueTime(parseTime(request.getDueTime()));
        todo.setUpdateTime(LocalDateTime.now());
        if (todo.getId() == null) {
            todo.setStatus(0);
            todo.setCreateTime(LocalDateTime.now());
            learningTodoMapper.insert(todo);
        } else {
            learningTodoMapper.updateById(todo);
        }
        return todo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long userId, Long id, Integer status) {
        LearningTodo todo = requireMine(userId, id);
        todo.setStatus(status == null ? 0 : status);
        todo.setUpdateTime(LocalDateTime.now());
        learningTodoMapper.updateById(todo);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long userId, Long id) {
        requireMine(userId, id);
        learningTodoMapper.deleteById(id);
    }

    /** 查询并校验待办归属 */
    private LearningTodo requireMine(Long userId, Long id) {
        LearningTodo todo = learningTodoMapper.selectById(id);
        if (todo == null) {
            throw new BusinessException(404, "待办不存在");
        }
        if (!Objects.equals(todo.getUserId(), userId)) {
            throw new BusinessException(403, "只能操作自己的待办");
        }
        return todo;
    }

    /** 解析时间字符串 */
    private LocalDateTime parseTime(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(text.trim(), DTF);
        } catch (Exception e) {
            throw new BusinessException(400, "时间格式应为 yyyy-MM-dd HH:mm:ss");
        }
    }

    /** 填充课程名称 */
    private void fillCourseName(LearningTodo todo) {
        if (todo.getCourseId() == null) {
            return;
        }
        Course course = courseMapper.selectById(todo.getCourseId());
        todo.setCourseName(course == null ? null : course.getCourseName());
    }
}
