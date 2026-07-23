package com.aetherlearn.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 章节小测作答记录实体（F-LEARN 扩展模块）
 * <p>对应表 {@code chapter_quiz_record}；记录学生每道题的作答、得分和判定结果。</p>
 */
@Data
@TableName("chapter_quiz_record")
public class ChapterQuizRecord implements Serializable {

    /** 记录ID（自增主键） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 课程ID */
    private Long courseId;

    /** 章节ID */
    private Long chapterId;

    /** 题目ID */
    private Long quizId;

    /** 学生ID */
    private Long studentId;

    /** 学生作答 */
    private String answer;

    /** 得分 */
    private Integer score;

    /** 是否正确：1-对 0-错 */
    private Integer isCorrect;

    /** 反馈/解析 */
    private String feedback;

    /** 提交时间 */
    private LocalDateTime createTime;
}
