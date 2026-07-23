package com.aetherlearn.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 章节小测保存请求（教师维护题目）
 */
@Data
public class ChapterQuizSaveRequest implements Serializable {

    /** 题目ID，编辑时填写 */
    private Long id;

    /** 课程ID */
    private Long courseId;

    /** 章节ID */
    private Long chapterId;

    /** 题型：1-单选 2-多选 3-判断 4-填空 */
    private Integer type;

    /** 题干 */
    private String content;

    /** 选项 */
    private List<String> options;

    /** 标准答案 */
    private String answer;

    /** 解析/提示 */
    private String analysis;

    /** 分值 */
    private Integer score;

    /** 序号 */
    private Integer seq;
}
