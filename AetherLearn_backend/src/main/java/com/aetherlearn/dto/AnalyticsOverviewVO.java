package com.aetherlearn.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 学生学情总览视图（F-LEARN-01/02/04）
 * <p>聚合学情概览卡、成绩趋势、知识盲区与个性化建议，返回给 {@code /api/dashboard/analytics/overview}。</p>
 */
@Data
public class AnalyticsOverviewVO implements Serializable {

    /** 学情概览卡（F-LEARN-01） */
    private Overview overview;

    /** 成绩趋势（F-LEARN-01） */
    private List<ScoreTrend> scoreTrend;

    /** 知识盲区（F-LEARN-02） */
    private List<KnowledgeGap> knowledgeGaps;

    /** 个性化学习建议（F-LEARN-04） */
    private List<Suggestion> suggestions;

    /** 学情概览卡 */
    @Data
    public static class Overview implements Serializable {
        private double avgAccuracy;
        private long activityCount;
        private long courseCount;
        private double avgScore;
    }

    /** 成绩趋势点 */
    @Data
    public static class ScoreTrend implements Serializable {
        private String title;
        private double score;
        private double fullScore;
    }

    /** 知识盲区项 */
    @Data
    public static class KnowledgeGap implements Serializable {
        private String knowledgePoint;
        private double errorRate;
        private double mastery;
        private boolean weak;
    }

    /** 个性化建议项 */
    @Data
    public static class Suggestion implements Serializable {
        private String content;
        private String type;
    }
}
