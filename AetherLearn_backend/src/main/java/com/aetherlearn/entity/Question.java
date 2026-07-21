package com.aetherlearn.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/**
 * 题目实体（F-HW 作业模块）
 * <p>对应表 {@code question}；题型：1-单选 2-多选 3-判断 4-填空 5-简答；
 * {@code options} 以 JSON 数组字符串存储（如 ["A","B","C","D"]），本表无软删除。</p>
 */
@Data
@TableName("question")
public class Question implements Serializable {

    /** 题目ID（自增主键） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属作业ID */
    private Long assignmentId;

    /** 题型：1-单选 2-多选 3-判断 4-填空 5-简答 */
    private Integer type;

    /** 题干 */
    private String content;

    /** 选项（JSON 数组字符串，如 "[\"A\",\"B\",\"C\",\"D\"]"） */
    private String options;

    /** 标准答案 */
    private String answer;

    /** 解析/参考答案 */
    private String analysis;

    /** 分值 */
    private Integer score;

    /** 关联知识点 */
    private String knowledgePoint;

    /** 题目序号 */
    private Integer seq;
}
