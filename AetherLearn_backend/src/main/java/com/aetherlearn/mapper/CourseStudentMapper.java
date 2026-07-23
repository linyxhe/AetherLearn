package com.aetherlearn.mapper;

import com.aetherlearn.entity.CourseStudent;
import com.aetherlearn.entity.SysUser;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 选课关系 Mapper（F-COURSE-03 学生加入管理）
 */
@Mapper
public interface CourseStudentMapper extends BaseMapper<CourseStudent> {

    /** 判断学生是否已加入某课程（用于重复加入校验） */
    @Select("SELECT COUNT(1) FROM course_student WHERE course_id = #{courseId} AND student_id = #{studentId}")
    int countByCourseAndStudent(Long courseId, Long studentId);

    /** 查询某课程下所有学生（JOIN sys_user 获取姓名等信息） */
    @Select("SELECT u.id, u.username, u.real_name, u.avatar, u.email, u.phone, cs.create_time AS join_time "
            + "FROM course_student cs JOIN sys_user u ON cs.student_id = u.id "
            + "WHERE cs.course_id = #{courseId} ORDER BY cs.create_time DESC")
    List<SysUser> selectStudentsByCourseId(@Param("courseId") Long courseId);

    /** 删除选课关系（教师移除学生） */
    @Delete("DELETE FROM course_student WHERE course_id = #{courseId} AND student_id = #{studentId}")
    int deleteByCourseAndStudent(@Param("courseId") Long courseId, @Param("studentId") Long studentId);
}
