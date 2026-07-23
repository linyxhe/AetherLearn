package com.aetherlearn.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 学习计划/待办实体（F-TODO）
 * <p>对应表 {@code learning_todo}；学生维护个人学习计划和复习待办。</p>
 */
@Data
@TableName("learning_todo")
public class LearningTodo implements Serializable {

    /** 待办ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 学生ID */
    private Long userId;

    /** 关联课程ID，可为空 */
    private Long courseId;

    /** 待办标题 */
    private String title;

    /** 待办内容 */
    private String content;

    /** 类型：PLAN/REVIEW/HOMEWORK/QUIZ */
    private String todoType;

    /** 优先级：1-低 2-中 3-高 */
    private Integer priority;

    /** 截止时间 */
    private LocalDateTime dueTime;

    /** 状态：0-未完成 1-已完成 */
    private Integer status;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 课程名称（非表字段） */
    @TableField(exist = false)
    private String courseName;
}
