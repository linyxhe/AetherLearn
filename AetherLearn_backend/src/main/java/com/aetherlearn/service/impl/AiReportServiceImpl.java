package com.aetherlearn.service.impl;

import com.aetherlearn.ai.AiConfig;
import com.aetherlearn.ai.PythonAiClient;
import com.aetherlearn.dto.AiLearningReportVO;
import com.aetherlearn.dto.AnalyticsOverviewVO;
import com.aetherlearn.service.AiReportService;
import com.aetherlearn.service.DashboardService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * AI 报告服务实现
 * <p>优先由 Python AI 服务（LangGraph）生成自然语言报告；不可用时用规则模板降级。</p>
 */
@Service
public class AiReportServiceImpl implements AiReportService {

    private final DashboardService dashboardService;
    private final AiConfig aiConfig;
    private final PythonAiClient pythonAiClient;

    public AiReportServiceImpl(DashboardService dashboardService, AiConfig aiConfig, PythonAiClient pythonAiClient) {
        this.dashboardService = dashboardService;
        this.aiConfig = aiConfig;
        this.pythonAiClient = pythonAiClient;
    }

    @Override
    public AiLearningReportVO generateStudentReport(Long studentId) {
        AnalyticsOverviewVO analytics = dashboardService.getAnalyticsOverview(studentId);
        AiLearningReportVO report = buildRuleReport(analytics);
        if (aiConfig.isAvailable()) {
            // 提示词模板在 Python 侧注册表，这里只提供变量值（沿用 Java 对象的格式化结果）
            Map<String, Object> variables = new LinkedHashMap<>();
            variables.put("overview", String.valueOf(analytics.getOverview()));
            variables.put("score_trend", String.valueOf(analytics.getScoreTrend()));
            variables.put("knowledge_gaps", String.valueOf(analytics.getKnowledgeGaps()));
            variables.put("suggestions", String.valueOf(analytics.getSuggestions()));
            variables.put("learning_path", String.valueOf(analytics.getLearningPath()));

            // 预算 20s：Python 侧只拿走 60%，剩下 40% 留给传输与排队。
            // 原先 6s 的预算配上推理型模型（思维链本身就要 3~4s）几乎必然超时降级。
            PythonAiClient.TaskResult result =
                    pythonAiClient.task("student_report", variables, "text", 20L);
            if (result.isSuccess() && !result.getRaw().isBlank()) {
                report.setAiText(result.getRaw().trim());
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

}
