package com.aetherlearn.dto;

import lombok.Data;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;

/**
 * 教师复核请求（F-HW 作业模块）
 * <p>教师对单题学生作答调整分数与反馈，并标记复核状态。</p>
 */
@Data
public class ReviewRequest implements Serializable {

    /** 学生作答记录ID */
    @NotNull(message = "作答记录ID不能为空")
    private Long answerId;

    /** 教师评定的本题得分 */
    @NotNull(message = "评分不能为空")
    @Min(value = 0, message = "评分不能为负数")
    private Integer score;

    /** 批改反馈 */
    private String feedback;

    /** 复核状态：1-已复核无异议 2-已修改分数 */
    private Integer reviewStatus;
}
