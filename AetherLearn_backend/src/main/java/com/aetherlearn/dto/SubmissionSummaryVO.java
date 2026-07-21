package com.aetherlearn.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 提交情况汇总视图对象（F-HW 作业模块）
 * <p>教师查看某作业提交情况时返回：按学生聚合其得分、作答题数、待复核题数等。</p>
 */
@Data
public class SubmissionSummaryVO implements Serializable {

    /** 学生ID */
    private Long studentId;
    /** 学生姓名 */
    private String studentName;
    /** 已得总分 */
    private Integer earnedScore;
    /** 题目总数 */
    private Integer questionCount;
    /** 已作答题数 */
    private Integer answeredCount;
    /** 待复核题数（主观题未复核） */
    private Integer pendingReview;
    /** 最近提交时间 */
    private LocalDateTime submitTime;
}
