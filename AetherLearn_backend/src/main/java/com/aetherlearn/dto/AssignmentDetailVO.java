package com.aetherlearn.dto;

import com.aetherlearn.entity.Assignment;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 作业详情视图对象（F-HW 作业模块）
 * <p>下发给学生作答或教师编辑时使用；{@code questions} 为题目列表。
 * 学生视角下 {@link QuestionVO#answer} 会被置空，避免提前泄露标准答案。</p>
 */
@Data
public class AssignmentDetailVO implements Serializable {

    /** 作业基本信息 */
    private Assignment assignment;

    /** 题目列表（按序号排序） */
    private List<QuestionVO> questions;

    /** 题目视图（内嵌） */
    @Data
    public static class QuestionVO implements Serializable {
        private Long id;
        private Integer type;
        private String content;
        private List<String> options;
        private String answer;        // 仅教师可见，学生视角为 null
        private String analysis;
        private Integer score;
        private String knowledgePoint;
        private Integer seq;
    }
}
