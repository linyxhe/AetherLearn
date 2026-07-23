package com.aetherlearn.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 课程在线学习章节实体（F-LEARN 在线学习模块）
 * <p>对应表 {@code course_chapter}；教师维护章节学习内容，学生完成章节后写入学习记录。</p>
 */
@Data
@TableName("course_chapter")
public class CourseChapter implements Serializable {

    /** 章节ID（自增主键） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属课程ID */
    private Long courseId;

    /** 章节标题 */
    private String title;

    /** 章节学习内容 */
    private String content;

    /** 资源类型：TEXT/VIDEO/PDF/WORD/FILE */
    private String resourceType;

    /** 章节资源文件路径（uploads/course） */
    private String resourceUrl;

    /** 预计学习时长（分钟） */
    private Integer durationMinutes;

    /** 排序序号 */
    private Integer sortNo;

    /** 状态：1-发布 0-草稿 */
    private Integer status;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 当前学生是否已完成（非表字段） */
    @TableField(exist = false)
    private Boolean completed;
}
