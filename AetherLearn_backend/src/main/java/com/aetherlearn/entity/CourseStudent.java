package com.aetherlearn.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 课程-学生选课关系实体（F-COURSE-03 学生加入管理）
 * <p>对应表 {@code course_student}。</p>
 */
@Data
@TableName("course_student")
public class CourseStudent implements Serializable {

    /** 关系ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 课程ID */
    private Long courseId;

    /** 学生ID */
    private Long studentId;

    /** 选课时间 */
    private LocalDateTime createTime;
}
