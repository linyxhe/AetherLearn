package com.aetherlearn.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 教师/管理员数据看板统计视图（F-DASH-01~06）
 * <p>聚合概览卡片与五类图表数据，一次性返回给前端 {@code /api/dashboard/stat}。</p>
 */
@Data
public class DashboardStatVO implements Serializable {

    /** 概览数字卡（F-DASH-01） */
    private Overview overview;

    /** 成绩分布（F-DASH-02）：各分数段人数 */
    private List<ScoreRange> scoreDistribution;

    /** 作业完成率趋势（F-DASH-03） */
    private List<CompletionTrend> completionTrend;

    /** 班级知识点掌握度（F-DASH-04） */
    private List<KnowledgeMastery> knowledgeMastery;

    /** 问答活跃度（F-DASH-05）：近 7 天 */
    private List<QaActivity> qaActivity;

    /** 学生排行榜（F-DASH-06） */
    private List<StudentRank> studentRanking;

    /** 概览数字卡 */
    @Data
    public static class Overview implements Serializable {
        private long courseCount;
        private long studentCount;
        private long assignmentCount;
        private long qaCount;
    }

    /** 成绩分布分段 */
    @Data
    public static class ScoreRange implements Serializable {
        private String range;
        private long count;
    }

    /** 作业完成率趋势点 */
    @Data
    public static class CompletionTrend implements Serializable {
        private String title;
        private double completionRate;
        private double avgScore;
    }

    /** 知识点掌握度点 */
    @Data
    public static class KnowledgeMastery implements Serializable {
        private String knowledgePoint;
        private double mastery;
    }

    /** 问答活跃度点 */
    @Data
    public static class QaActivity implements Serializable {
        private String dateLabel;
        private long count;
    }

    /** 学生排行榜项 */
    @Data
    public static class StudentRank implements Serializable {
        private String studentName;
        private double avgScore;
        private double completionRate;
        /** 学情预警：低分或低完成率时为 true（F-LEARN-05） */
        private boolean warning;
        /** 预警原因（如"低分""低完成率"） */
        private String warningReason;
    }
}
