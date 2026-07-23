package com.aetherlearn.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 章节小测提交请求（学生作答）
 */
@Data
public class ChapterQuizSubmitRequest implements Serializable {

    /** 课程ID */
    @NotNull(message = "课程ID不能为空")
    private Long courseId;

    /** 章节ID */
    @NotNull(message = "章节ID不能为空")
    private Long chapterId;

    /** 作答明细 */
    @NotEmpty(message = "作答内容不能为空")
    @Valid
    private List<AnswerItem> answers;

    /**
     * 单题作答项
     */
    @Data
    public static class AnswerItem implements Serializable {
        /** 题目ID */
        @NotNull(message = "题目ID不能为空")
        private Long quizId;

        /** 学生作答 */
        private String answer;
    }
}
