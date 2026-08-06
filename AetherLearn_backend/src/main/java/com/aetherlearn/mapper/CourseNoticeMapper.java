package com.aetherlearn.mapper;

import com.aetherlearn.entity.CourseNotice;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 课程公告 Mapper（F-NOTIFY）
 */
@Mapper
public interface CourseNoticeMapper extends BaseMapper<CourseNotice> {

    /**
     * 查询学生可见公告：仅限已加入课程且已发布公告。
     */
    @Select("SELECT n.* FROM course_notice n " +
        "JOIN course_student cs ON n.course_id = cs.course_id " +
            "JOIN course c ON c.id = n.course_id " +
            "WHERE cs.student_id = #{studentId} AND n.status = 1 " +
            "AND c.status = 1 AND c.is_deleted = 0 " +
            "ORDER BY n.create_time DESC")
    List<CourseNotice> selectVisibleByStudent(@Param("studentId") Long studentId);
}
