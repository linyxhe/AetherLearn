package com.aetherlearn.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 作答提交请求（F-HW 作业模块）
 * <p>学生一次性提交某作业的全部题目作答；{@code answers} 为各题作答内容列表。</p>
 */
@Data
public class AnswerSubmitRequest implements Serializable {

    /** 作业ID */
    @NotNull(message = "作业ID不能为空")
    private Long assignmentId;

    /** 各题作答内容（题目ID + 学生作答文本） */
    @NotEmpty(message = "作答内容不能为空")
    @Valid
    private List<AnswerItem> answers;

    /** 单题作答项（内嵌） */
    @Data
    public static class AnswerItem implements Serializable {
        /** 题目ID */
        @NotNull(message = "题目ID不能为空")
        private Long questionId;
        /** 学生作答内容 */
        private String content;
        /** 作答图片路径（可选，uploads/answer/） */
        private String imageUrl;
    }
}
