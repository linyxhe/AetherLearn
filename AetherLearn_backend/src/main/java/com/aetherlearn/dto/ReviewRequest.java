package com.aetherlearn.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 教师复核请求（F-HW 作业模块）
 * <p>教师对单题学生作答调整分数与反馈，并标记复核状态。</p>
 */
@Data
public class ReviewRequest implements Serializable {

    /** 学生作答记录ID */
    private Long answerId;

    /** 教师评定的本题得分 */
    private Integer score;

    /** 批改反馈 */
    private String feedback;

    /** 复核状态：1-已复核无异议 2-已修改分数 */
    private Integer reviewStatus;
}
