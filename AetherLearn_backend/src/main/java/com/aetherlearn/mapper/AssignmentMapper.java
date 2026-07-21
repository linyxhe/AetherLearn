package com.aetherlearn.mapper;

import com.aetherlearn.entity.Assignment;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 作业 Mapper（F-HW 作业模块）
 * <p>继承 {@code BaseMapper} 获得基础 CRUD；按角色/学生查询作业列表使用自定义 SQL。</p>
 */
@Mapper
public interface AssignmentMapper extends BaseMapper<Assignment> {

    /**
     * 教师/管理员视角：列出作业
     * <p>管理员（role=1）可见全部；教师仅可见本人课程下的作业；可按课程筛选。
     * 逻辑删除字段需手动附加 {@code is_deleted=0}。</p>
     */
    @Select("<script>" +
            "SELECT a.* FROM assignment a " +
            "WHERE a.is_deleted = 0 " +
            "AND ( #{role} = 1 OR a.course_id IN (SELECT id FROM course WHERE teacher_id = #{userId}) ) " +
            "<if test='courseId != null'> AND a.course_id = #{courseId} </if> " +
            "ORDER BY a.create_time DESC" +
            "</script>")
    List<Assignment> selectByTeacherOrAdmin(@Param("userId") Long userId,
                                            @Param("role") Integer role,
                                            @Param("courseId") Long courseId);

    /**
     * 学生视角：列出已加入课程下的作业，可按课程筛选。
     */
    @Select("<script>" +
            "SELECT a.* FROM assignment a " +
            "JOIN course_student cs ON a.course_id = cs.course_id " +
            "WHERE a.is_deleted = 0 AND cs.student_id = #{userId} " +
            "<if test='courseId != null'> AND a.course_id = #{courseId} </if> " +
            "ORDER BY a.create_time DESC" +
            "</script>")
    List<Assignment> selectByStudent(@Param("userId") Long userId,
                                     @Param("courseId") Long courseId);
}
