package com.aetherlearn.mapper;

import com.aetherlearn.entity.CourseStudent;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * 选课关系 Mapper（F-COURSE-03 学生加入管理）
 */
@Mapper
public interface CourseStudentMapper extends BaseMapper<CourseStudent> {

    /** 判断学生是否已加入某课程（用于重复加入校验） */
    @Select("SELECT COUNT(1) FROM course_student WHERE course_id = #{courseId} AND student_id = #{studentId}")
    int countByCourseAndStudent(Long courseId, Long studentId);
}
