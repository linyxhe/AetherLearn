package com.aetherlearn.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 错题本条目视图对象
 * <p>统一封装作业错题与章节小测错题，供学生端错题本页面展示。</p>
 */
@Data
public class WrongBookItemVO implements Serializable {

    /** 来源类型：ASSIGNMENT / CHAPTER_QUIZ */
    private String sourceType;

    /** 课程ID */
    private Long courseId;

    /** 课程名称 */
    private String courseName;

    /** 来源标题（作业标题 / 章节标题） */
    private String sourceTitle;

    /** 章节ID（章节小测专用） */
    private Long chapterId;

    /** 章节标题（章节小测专用） */
    private String chapterTitle;

    /** 题目ID（作业题目 / 章节小测题目） */
    private Long questionId;

    /** 题型 */
    private Integer type;

    /** 题干 */
    private String content;

    /** 选项 */
    private List<String> options;

    /** 标准答案 */
    private String standardAnswer;

    /** 我的答案 */
    private String yourAnswer;

    /** 解析 / 反馈 */
    private String analysis;

    /** 本题得分 */
    private Integer score;

    /** 创建时间 */
    private LocalDateTime createTime;
}
