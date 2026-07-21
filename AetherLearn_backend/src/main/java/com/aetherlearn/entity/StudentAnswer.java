package com.aetherlearn.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 学生作答实体（F-HW 作业模块）
 * <p>对应表 {@code student_answer}；记录单题作答与批改结果。
 * {@code isCorrect} 仅客观题使用（1-对 0-错），主观题置 NULL；
 * {@code gradeType}：1-自动 2-人工复核；{@code reviewStatus}：0-待复核 1-已复核无异议 2-已修改分数。</p>
 */
@Data
@TableName("student_answer")
public class StudentAnswer implements Serializable {

    /** 作答ID（自增主键） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 作业ID */
    private Long assignmentId;

    /** 题目ID */
    private Long questionId;

    /** 学生ID */
    private Long studentId;

    /** 学生作答内容 */
    private String answer;

    /** 本题得分 */
    private Integer score;

    /** 是否正确：1-对 0-错（客观题）；主观题为 NULL */
    private Integer isCorrect;

    /** 批改反馈 */
    private String feedback;

    /** 批改方式：1-自动 2-人工复核 */
    private Integer gradeType;

    /** 复核状态：0-待复核 1-已复核无异议 2-已修改分数（v2.0 新增） */
    private Integer reviewStatus;

    /** 提交时间 */
    private LocalDateTime createTime;
}
