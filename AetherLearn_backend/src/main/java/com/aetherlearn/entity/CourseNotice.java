package com.aetherlearn.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 课程公告实体（F-NOTIFY-01/02）
 * <p>教师面向课程发布公告，学生按已加入课程查看。</p>
 */
@Data
@TableName("course_notice")
public class CourseNotice implements Serializable {

    /** 公告ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属课程ID */
    private Long courseId;

    /** 公告标题 */
    private String title;

    /** 公告内容 */
    private String content;

    /** 公告类型：NOTICE-公告 HOMEWORK-作业提醒 RESOURCE-资料更新 */
    private String noticeType;

    /** 发布状态：1-已发布 0-草稿 */
    private Integer status;

    /** 发布人ID */
    private Long createBy;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
