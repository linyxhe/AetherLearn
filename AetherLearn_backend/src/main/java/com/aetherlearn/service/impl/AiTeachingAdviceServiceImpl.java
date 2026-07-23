package com.aetherlearn.service.impl;

import com.aetherlearn.ai.AiConfig;
import com.aetherlearn.ai.LlmClient;
import com.aetherlearn.dto.AiTeachingAdviceVO;
import com.aetherlearn.dto.DashboardStatVO;
import com.aetherlearn.service.AiTeachingAdviceService;
import com.aetherlearn.service.DashboardService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * AI 教学建议服务实现
 */
@Service
public class AiTeachingAdviceServiceImpl implements AiTeachingAdviceService {

    private final DashboardService dashboardService;
    private final AiConfig aiConfig;
    private final LlmClient llmClient;

    public AiTeachingAdviceServiceImpl(DashboardService dashboardService, AiConfig aiConfig, LlmClient llmClient) {
        this.dashboardService = dashboardService;
        this.aiConfig = aiConfig;
        this.llmClient = llmClient;
    }

    @Override
    public AiTeachingAdviceVO generateTeachingAdvice(Long teacherId, Integer role) {
        DashboardStatVO stat = dashboardService.getDashboardStat(teacherId, role);
        AiTeachingAdviceVO vo = buildRuleAdvice(stat);
        if (aiConfig.isAvailable()) {
            String text = llmClient.chatWithin(buildSystemPrompt(), buildUserPrompt(stat), 6);
            if (text != null && !text.isBlank()) {
                vo.setAiText(text.trim());
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

    private String buildSystemPrompt() {
        return "你是 AetherLearn 教学分析助手。请基于教师看板数据生成中文教学建议，语气专业、简洁、可执行。";
    }

    private String buildUserPrompt(DashboardStatVO stat) {
        return "请生成一份面向教师的教学建议，包含总体判断、重点知识点、预警学生和后续教学动作。\n"
                + "概览：" + stat.getOverview()
                + "\n成绩分布：" + stat.getScoreDistribution()
                + "\n完成率趋势：" + stat.getCompletionTrend()
                + "\n知识掌握：" + stat.getKnowledgeMastery()
                + "\n预警学生：" + stat.getStudentRanking();
    }
}
