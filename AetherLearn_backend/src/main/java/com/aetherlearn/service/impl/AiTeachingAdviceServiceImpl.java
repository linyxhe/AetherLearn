package com.aetherlearn.service.impl;

import com.aetherlearn.ai.AiConfig;
import com.aetherlearn.ai.PythonAiClient;
import com.aetherlearn.dto.AiTeachingAdviceVO;
import com.aetherlearn.dto.DashboardStatVO;
import com.aetherlearn.service.AiTeachingAdviceService;
import com.aetherlearn.service.DashboardService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * AI 教学建议服务实现
 * <p>优先由 Python AI 服务（LangGraph）生成；不可用时用规则模板降级。</p>
 */
@Service
public class AiTeachingAdviceServiceImpl implements AiTeachingAdviceService {

    private final DashboardService dashboardService;
    private final AiConfig aiConfig;
    private final PythonAiClient pythonAiClient;

    public AiTeachingAdviceServiceImpl(DashboardService dashboardService, AiConfig aiConfig, PythonAiClient pythonAiClient) {
        this.dashboardService = dashboardService;
        this.aiConfig = aiConfig;
        this.pythonAiClient = pythonAiClient;
    }

    @Override
    public AiTeachingAdviceVO generateTeachingAdvice(Long teacherId, Integer role) {
        DashboardStatVO stat = dashboardService.getDashboardStat(teacherId, role);
        AiTeachingAdviceVO vo = buildRuleAdvice(stat);
        if (aiConfig.isAvailable()) {
            Map<String, Object> variables = new LinkedHashMap<>();
            variables.put("overview", String.valueOf(stat.getOverview()));
            variables.put("score_distribution", String.valueOf(stat.getScoreDistribution()));
            variables.put("completion_trend", String.valueOf(stat.getCompletionTrend()));
            variables.put("knowledge_mastery", String.valueOf(stat.getKnowledgeMastery()));
            variables.put("student_ranking", String.valueOf(stat.getStudentRanking()));

            // 预算 20s：Python 侧只拿走 60%，剩下 40% 留给传输与排队。
            // 原先 6s 的预算配上推理型模型（思维链本身就要 3~4s）几乎必然超时降级。
            PythonAiClient.TaskResult result =
                    pythonAiClient.task("teacher_advice", variables, "text", 20L);
            if (result.isSuccess() && !result.getRaw().isBlank()) {
                vo.setAiText(result.getRaw().trim());
                vo.setAiGenerated(true);
            }
        }
        return vo;
    }

    private AiTeachingAdviceVO buildRuleAdvice(DashboardStatVO stat) {
        AiTeachingAdviceVO vo = new AiTeachingAdviceVO();
        vo.setOverview(stat.getOverview());
        vo.setAiGenerated(false);
        vo.setReportTime(LocalDateTime.now());
        vo.setSummary(buildSummary(stat));
        vo.setFocusPoints(buildFocusPoints(stat));
        vo.setWarningStudents(buildWarningStudents(stat));
        vo.setActions(buildActions(stat));
        return vo;
    }

    private String buildSummary(DashboardStatVO stat) {
        long warningCount = stat.getStudentRanking() == null ? 0 : stat.getStudentRanking().stream().filter(DashboardStatVO.StudentRank::isWarning).count();
        if (warningCount >= 5) {
            return "当前班级存在较明显的学情分化，建议优先处理预警学生和低完成率课程。";
        }
        if (warningCount > 0) {
            return "班级整体运行正常，但仍有少量学生需要及时跟进。";
        }
        return "班级当前表现较稳，可考虑适度增加综合练习和拓展内容。";
    }

    private List<String> buildFocusPoints(DashboardStatVO stat) {
        List<String> list = new ArrayList<>();
        if (stat.getKnowledgeMastery() != null) {
            stat.getKnowledgeMastery().stream()
                    .filter(k -> k.getMastery() < 70)
                    .limit(3)
                    .forEach(k -> list.add(k.getKnowledgePoint() + " 掌握度偏低，建议下次课回讲并配套练习。"));
        }
        if (list.isEmpty()) {
            list.add("当前班级知识掌握较均衡，可继续推进新内容。");
        }
        return list;
    }

    private List<String> buildWarningStudents(DashboardStatVO stat) {
        if (stat.getStudentRanking() == null) {
            return List.of("暂无预警学生");
        }
        List<String> list = stat.getStudentRanking().stream()
                .filter(DashboardStatVO.StudentRank::isWarning)
                .limit(6)
                .map(s -> s.getStudentName() + "（" + s.getWarningReason() + "）")
                .collect(Collectors.toList());
        return list.isEmpty() ? List.of("暂无预警学生") : list;
    }

    private List<String> buildActions(DashboardStatVO stat) {
        List<String> actions = new ArrayList<>();
        actions.add("优先安排一次针对薄弱知识点的讲评课，并配套 3~5 道小练习。");
        actions.add("对预警学生建立跟踪名单，课后查看错题本与待办完成情况。");
        actions.add("下次作业可增加 1~2 道章节小测同源题，检验即时掌握情况。");
        return actions;
    }

}
