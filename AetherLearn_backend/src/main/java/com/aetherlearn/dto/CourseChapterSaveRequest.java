package com.aetherlearn.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 课程章节保存请求 DTO
 * <p>用于教师新增/编辑在线学习章节。</p>
 */
@Data
public class CourseChapterSaveRequest {

    /** 章节ID，编辑时必传 */
    private Long id;

    /** 所属课程ID */
    @NotNull(message = "课程ID不能为空")
    private Long courseId;

    /** 章节标题 */
    @NotBlank(message = "章节标题不能为空")
    private String title;

    /** 章节正文内容 */
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
}
