package com.aetherlearn.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 课程公告保存请求（F-NOTIFY-01）
 */
@Data
public class CourseNoticeSaveRequest {

    /** 公告ID，编辑时传入 */
    private Long id;

    /** 课程ID */
    @NotNull(message = "课程ID不能为空")
    private Long courseId;

    /** 标题 */
    @NotBlank(message = "公告标题不能为空")
    private String title;

    /** 内容 */
    @NotBlank(message = "公告内容不能为空")
    private String content;

    /** 类型 */
    private String noticeType;

    /** 状态 */
    private Integer status;
}
