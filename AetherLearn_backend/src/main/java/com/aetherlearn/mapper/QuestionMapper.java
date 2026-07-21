package com.aetherlearn.mapper;

import com.aetherlearn.entity.Question;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 题目 Mapper（F-HW 作业模块）
 * <p>继承 {@code BaseMapper} 获得基础 CRUD；提供按作业批量取题（用于下发与批改）。</p>
 */
@Mapper
public interface QuestionMapper extends BaseMapper<Question> {

    /**
     * 查询某个作业的全部题目（按序号、ID 升序，保证展示顺序稳定）
     */
    @Select("SELECT * FROM question WHERE assignment_id = #{assignmentId} ORDER BY seq ASC, id ASC")
    List<Question> selectByAssignmentId(Long assignmentId);
}
