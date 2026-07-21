package com.aetherlearn.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 题目保存请求（F-HW 作业模块）
 * <p>教师新增/编辑题目共用；{@code id} 为空表示新增，否则为编辑。
 * {@code options} 以 List 接收，由 Service 序列化为 JSON 字符串入库。</p>
 */
@Data
public class QuestionSaveRequest implements Serializable {

    /** 题目ID（编辑时必填，新增时为空） */
    private Long id;

    /** 所属作业ID */
    private Long assignmentId;

    /** 题型：1-单选 2-多选 3-判断 4-填空 5-简答 */
    private Integer type;

    /** 题干 */
    private String content;

    /** 选项（单选/多选使用，如 ["A","B","C","D"]） */
    private List<String> options;

    /** 标准答案（判断题为"正确"/"错误"；填空为关键词；简答为要点） */
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
