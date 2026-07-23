package com.aetherlearn.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 章节小测题目实体（F-LEARN 扩展模块）
 * <p>对应表 {@code chapter_quiz}；教师围绕章节维护小测题目，学生在学习页即时答题。</p>
 */
@Data
@TableName("chapter_quiz")
public class ChapterQuiz implements Serializable {

    /** 题目ID（自增主键） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属课程ID */
    private Long courseId;

    /** 所属章节ID */
    private Long chapterId;

    /** 题型：1-单选 2-多选 3-判断 4-填空 */
    private Integer type;

    /** 题干 */
    private String content;

    /** 选项（JSON 数组字符串） */
    private String options;

    /** 标准答案 */
    private String answer;

    /** 解析/提示 */
    private String analysis;

    /** 分值 */
    private Integer score;

    /** 题目序号 */
    private Integer seq;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
