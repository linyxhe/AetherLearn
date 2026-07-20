package com.aetherlearn.mapper;

import com.aetherlearn.entity.Course;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 课程 Mapper（F-COURSE 课程管理模块）
 * <p>继承 {@code BaseMapper} 获得基础 CRUD；{@code is_deleted} 软删除由 MyBatis-Plus 全局逻辑删除自动处理。</p>
 */
@Mapper
public interface CourseMapper extends BaseMapper<Course> {

    /**
     * 查询某学生已加入的课程（JOIN 选课关系表）
     * <p>自定义 SQL 需手动过滤 {@code is_deleted=0}（逻辑删除仅在 MP 自动生成的 SQL 中生效）。</p>
     */
    @Select("SELECT c.* FROM course c JOIN course_student cs ON c.id = cs.course_id " +
            "WHERE cs.student_id = #{studentId} AND c.is_deleted = 0")
    List<Course> selectByStudentId(Long studentId);
}
