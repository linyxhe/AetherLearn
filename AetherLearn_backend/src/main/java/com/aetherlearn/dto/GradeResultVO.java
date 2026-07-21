package com.aetherlearn.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 批改结果视图对象（F-HW 作业模块）
 * <p>学生提交后或查看结果时返回：含作业总分、已得总分、各题明细
 * （对错/得分/反馈/是否经 AI 批改）。标准答案与解析在提交后对学生可见，用于讲评。</p>
 */
@Data
public class GradeResultVO implements Serializable {

    /** 作业ID */
    private Long assignmentId;
    /** 作业标题 */
    private String assignmentTitle;
    /** 作业总分 */
    private Integer totalScore;
    /** 学生已得总分 */
    private Integer earnedScore;
    /** 是否已提交过（用于前端区分"去做"与"看结果"） */
    private Boolean submitted;
    /** 各题批改明细 */
    private List<GradeItemVO> items;

    /** 单题批改明细（内嵌） */
    @Data
    public static class GradeItemVO implements Serializable {
        private Long questionId;
        /** 学生作答记录ID（教师复核时回传） */
        private Long answerId;
        private Integer type;
        private String content;
        private List<String> options;
        private String yourAnswer;       // 学生作答
        private String standardAnswer;   // 标准答案（讲评展示）
        private String analysis;         // 解析
        private Integer score;           // 本题得分
        private Boolean correct;         // 是否正确（客观题）；主观题为 null
        private String feedback;         // 批改反馈
        private Integer gradeType;       // 1-自动 2-人工复核
        private Integer reviewStatus;    // 0-待复核 1-已复核无异议 2-已修改分数
        private Boolean aiGraded;        // 是否经 AI 批改（主观题）
    }
}
