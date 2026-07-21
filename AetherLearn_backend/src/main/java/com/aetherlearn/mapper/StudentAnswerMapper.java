package com.aetherlearn.mapper;

import com.aetherlearn.entity.StudentAnswer;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 学生作答 Mapper（F-HW 作业模块）
 * <p>继承 {@code BaseMapper} 获得基础 CRUD；提供按作业/学生维度查询，用于批改与复核。</p>
 */
@Mapper
public interface StudentAnswerMapper extends BaseMapper<StudentAnswer> {

    /** 查询某学生对某作业的作答明细（全部题目） */
    @Select("SELECT * FROM student_answer WHERE assignment_id = #{assignmentId} AND student_id = #{studentId}")
    List<StudentAnswer> selectByAssignmentAndStudent(@Param("assignmentId") Long assignmentId,
                                                     @Param("studentId") Long studentId);

    /** 查询某作业下已提交作答的去重学生ID 列表（教师复核用） */
    @Select("SELECT DISTINCT student_id FROM student_answer WHERE assignment_id = #{assignmentId} ORDER BY student_id")
    List<Long> selectStudentIdsByAssignment(Long assignmentId);

    /** 统计某学生是否已提交某作业（用于前端判断是否已作答） */
    @Select("SELECT COUNT(1) FROM student_answer WHERE assignment_id = #{assignmentId} AND student_id = #{studentId}")
    int countByAssignmentAndStudent(@Param("assignmentId") Long assignmentId,
                                    @Param("studentId") Long studentId);
}
