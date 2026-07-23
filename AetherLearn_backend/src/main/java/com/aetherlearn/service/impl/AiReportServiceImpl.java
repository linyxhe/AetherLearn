package com.aetherlearn.service.impl;

import com.aetherlearn.ai.AiConfig;
import com.aetherlearn.ai.LlmClient;
import com.aetherlearn.dto.AiLearningReportVO;
import com.aetherlearn.dto.AnalyticsOverviewVO;
import com.aetherlearn.service.AiReportService;
import com.aetherlearn.service.DashboardService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * AI 报告服务实现
 * <p>优先使用大模型生成自然语言报告；不可用时用规则模板降级，保证离线演示可用。</p>
 */
@Service
public class AiReportServiceImpl implements AiReportService {

    private final DashboardService dashboardService;
    private final AiConfig aiConfig;
    private final LlmClient llmClient;

    public AiReportServiceImpl(DashboardService dashboardService, AiConfig aiConfig, LlmClient llmClient) {
        this.dashboardService = dashboardService;
        this.aiConfig = aiConfig;
        this.llmClient = llmClient;
    }

    @Override
    public AiLearningReportVO generateStudentReport(Long studentId) {
        AnalyticsOverviewVO analytics = dashboardService.getAnalyticsOverview(studentId);
        AiLearningReportVO report = buildRuleReport(analytics);
        if (aiConfig.isAvailable()) {
            String text = llmClient.chatWithin(buildSystemPrompt(), buildUserPrompt(analytics), 6);
            if (text != null && !text.isBlank()) {
                report.setAiText(text.trim());
                report.setAiGenerated(true);
            }
        }
        return report;
    }

    /** 构造规则兜底报告 */
    private AiLearningReportVO buildRuleReport(AnalyticsOverviewVO analytics) {
        AiLearningReportVO report = new AiLearningReportVO();
        AnalyticsOverviewVO.Overview overview = analytics.getOverview() == null ? new AnalyticsOverviewVO.Overview() : analytics.getOverview();
        report.setOverview(overview);
        report.setAiGenerated(false);
        report.setReportTime(LocalDateTime.now());
        report.setOverallComment(overallComment(overview));
        report.setStrengths(buildStrengths(analytics));
        report.setWeaknesses(buildWeaknesses(analytics));
        report.setNextActions(buildNextActions(analytics));
        return report;
    }

    private String overallComment(AnalyticsOverviewVO.Overview overview) {
        if (overview.getAvgAccuracy() >= 85) {
            return "整体掌握较稳，当前学习表现处于良好区间，可以进入更高难度的综合练习。";
        }
        if (overview.getAvgAccuracy() >= 60) {
            return "整体学习进度正常，但仍存在若干知识点反复出错，建议用错题本和章节复习做一次集中巩固。";
        }
        return "当前基础掌握还不够稳定，需要先回到课程章节和基础题，按计划完成一轮补弱。";
    }

    private List<String> buildStrengths(AnalyticsOverviewVO analytics) {
        List<String> result = new ArrayList<>();
        AnalyticsOverviewVO.Overview overview = analytics.getOverview();
        if (overview != null && overview.getActivityCount() > 0) {
            result.add("已有稳定学习行为记录，系统能够持续追踪你的课程学习轨迹。");
        }
        if (overview != null && overview.getAvgAccuracy() >= 80) {
            result.add("平均正确率较高，说明多数基础知识点已经形成稳定掌握。");
        }
        if (analytics.getScoreTrend() != null && analytics.getScoreTrend().size() >= 2) {
            result.add("已有多次成绩记录，可以结合趋势变化调整复习节奏。");
        }
        if (result.isEmpty()) {
            result.add("已开始积累课程学习数据，继续完成章节和作业后报告会更准确。");
        }
        return result;
    }

    private List<String> buildWeaknesses(AnalyticsOverviewVO analytics) {
        if (analytics.getKnowledgeGaps() == null || analytics.getKnowledgeGaps().isEmpty()) {
            return List.of("暂未发现稳定薄弱点，建议继续完成作业和小测以积累数据。");
        }
        List<String> gaps = analytics.getKnowledgeGaps().stream()
                .filter(AnalyticsOverviewVO.KnowledgeGap::isWeak)
                .map(gap -> gap.getKnowledgePoint() + "（错误率 " + Math.round(gap.getErrorRate()) + "%）")
                .collect(Collectors.toList());
        return gaps.isEmpty() ? List.of("当前薄弱点不明显，建议保持正常节奏复习。") : gaps;
    }

    private List<String> buildNextActions(AnalyticsOverviewVO analytics) {
        List<String> actions = new ArrayList<>();
        if (analytics.getLearningPath() != null && !analytics.getLearningPath().isEmpty()) {
            analytics.getLearningPath().stream().limit(3).forEach(step ->
                    actions.add("按学习路径完成：" + step.getTitle() + "，当前掌握度 " + Math.round(step.getMastery()) + "%。"));
        }
        if (analytics.getSuggestions() != null) {
            analytics.getSuggestions().stream().limit(2).forEach(s -> actions.add(s.getContent()));
        }
        actions.add("每天复盘错题本中未掌握题目，并把复习安排加入学习计划。");
        return actions;
    }

    private String buildSystemPrompt() {
        return "你是 AetherLearn 学习分析助手。请基于学生学情数据生成中文学习报告，语气客观、具体、可执行。";
    }

    private String buildUserPrompt(AnalyticsOverviewVO analytics) {
        return "请生成一份 300 字以内的学习报告，包含总体表现、优势、薄弱点、下周行动建议。\n"
                + "概览：" + analytics.getOverview()
                + "\n成绩趋势：" + analytics.getScoreTrend()
                + "\n知识盲区：" + analytics.getKnowledgeGaps()
                + "\n学习建议：" + analytics.getSuggestions()
                + "\n学习路径：" + analytics.getLearningPath();
    }
}
