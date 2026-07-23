package com.aetherlearn.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 章节笔记保存请求（F-NOTE-01/02）
 */
@Data
public class ChapterNoteSaveRequest {

    /** 课程ID */
    @NotNull(message = "课程ID不能为空")
    private Long courseId;

    /** 章节ID */
    @NotNull(message = "章节ID不能为空")
    private Long chapterId;

    /** 标题 */
    private String title;

    /** 内容 */
    @NotBlank(message = "笔记内容不能为空")
    private String content;

    /** 是否收藏 */
    private Integer favorite;
}
