package com.aetherlearn.mapper;

import com.aetherlearn.entity.LearningTodo;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 学习计划/待办 Mapper
 */
@Mapper
public interface LearningTodoMapper extends BaseMapper<LearningTodo> {
}
