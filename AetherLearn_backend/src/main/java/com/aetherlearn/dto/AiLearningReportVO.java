package com.aetherlearn.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * AI 学习报告视图对象
 */
@Data
public class AiLearningReportVO implements Serializable {

    /** 学情概览 */
    private AnalyticsOverviewVO.Overview overview;

    /** 总体评价 */
    private String overallComment;

    /** 学习优势 */
    private List<String> strengths;

    /** 薄弱点 */
    private List<String> weaknesses;

    /** 下一步行动 */
    private List<String> nextActions;

    /** AI 原文报告（大模型可用时返回） */
    private String aiText;

    /** 是否由 AI 生成 */
    private Boolean aiGenerated;

    /** 报告生成时间 */
    private LocalDateTime reportTime;
}
