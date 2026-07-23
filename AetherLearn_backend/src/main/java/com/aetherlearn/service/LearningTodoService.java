package com.aetherlearn.service;

import com.aetherlearn.dto.LearningTodoSaveRequest;
import com.aetherlearn.entity.LearningTodo;

import java.util.List;

/**
 * 学习计划/待办服务接口
 */
public interface LearningTodoService {

    /** 查询我的学习待办 */
    List<LearningTodo> listMine(Long userId, Integer status, Long courseId);

    /** 新增/编辑学习待办 */
    LearningTodo save(Long userId, LearningTodoSaveRequest request);

    /** 更新完成状态 */
    void updateStatus(Long userId, Long id, Integer status);

    /** 删除学习待办 */
    void delete(Long userId, Long id);
}
