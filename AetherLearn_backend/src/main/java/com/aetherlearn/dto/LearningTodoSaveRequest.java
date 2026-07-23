package com.aetherlearn.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

/**
 * 学习待办保存请求
 */
@Data
public class LearningTodoSaveRequest implements Serializable {

    /** 待办ID，编辑时填写 */
    private Long id;

    /** 关联课程ID，可为空 */
    private Long courseId;

    /** 待办标题 */
    @NotBlank(message = "待办标题不能为空")
    private String title;

    /** 待办内容 */
    private String content;

    /** 类型：PLAN/REVIEW/HOMEWORK/QUIZ */
    private String todoType;

    /** 优先级：1-低 2-中 3-高 */
    private Integer priority;

    /** 截止时间：yyyy-MM-dd HH:mm:ss */
    private String dueTime;
}
