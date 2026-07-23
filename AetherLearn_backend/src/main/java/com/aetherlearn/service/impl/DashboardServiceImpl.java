package com.aetherlearn.service.impl;

import com.aetherlearn.dto.AnalyticsOverviewVO;
import com.aetherlearn.dto.DashboardStatVO;
import com.aetherlearn.mapper.StatMapper;
import com.aetherlearn.service.DashboardService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 看板与学情统计服务实现（F-DASH / F-LEARN）
 * <p>所有统计按"作用域课程"隔离：管理员看全平台，教师仅看本人课程。</p>
 */
@Service
public class DashboardServiceImpl implements DashboardService {

    /** 成绩分布分段（L5 可配置，默认 5 段） */
    @Value("${aetherlearn.score-ranges:0-59,60-69,70-79,80-89,90-100}")
    private String scoreRangesStr;

    /** 知识盲区判定阈值（L6 可配置）：错误率 ≥ 阈值视为薄弱 */
    @Value("${aetherlearn.weak-threshold:40.0}")
    private double weakThreshold;

    /** 学习建议数量上限（L4 可配置） */
    @Value("${aetherlearn.suggestion-limit:10}")
    private int suggestionLimit;

    /** 学情预警阈值（F-LEARN-05）：平均分 < 60 或完成率 < 50% 视为预警 */
    private static final double WARN_SCORE_THRESHOLD = 60.0;
    private static final double WARN_COMPLETION_THRESHOLD = 50.0;

    private final StatMapper statMapper;

    public DashboardServiceImpl(StatMapper statMapper) {
        this.statMapper = statMapper;
    }

    /** 获取成绩分布分段数组（L5 可配置） */
    private String[] getScoreRanges() {
        return scoreRangesStr.split(",");
    }

    @Override
    public DashboardStatVO getDashboardStat(Long userId, Integer role) {
        DashboardStatVO vo = new DashboardStatVO();

        // 作用域课程：教师仅本人课程，管理员全部
        List<Long> courseIds = statMapper.selectVisibleCourseIds(userId, role);

        // 若作用域内无课程，直接返回空结构（避免 IN () 语法错误）
        if (courseIds.isEmpty()) {
            vo.setOverview(buildEmptyOverview());
            vo.setScoreDistribution(buildEmptyScoreDistribution());
            vo.setCompletionTrend(List.of());
            vo.setKnowledgeMastery(List.of());
            vo.setQaActivity(buildQaActivity(null));
            vo.setStudentRanking(List.of());
            return vo;
        }

        // F-DASH-01 概览卡片
        DashboardStatVO.Overview overview = new DashboardStatVO.Overview();
        overview.setCourseCount(courseIds.size());
        overview.setStudentCount(statMapper.countStudents(courseIds));
        overview.setAssignmentCount(statMapper.countAssignments(courseIds));
        overview.setQaCount(statMapper.countQa(courseIds));
        vo.setOverview(overview);

        // F-DASH-02 成绩分布
        vo.setScoreDistribution(buildScoreDistribution(statMapper.selectScoreDistribution(courseIds)));

        // F-DASH-03 作业完成率趋势
        long totalAssignments = overview.getAssignmentCount();
        vo.setCompletionTrend(buildCompletionTrend(statMapper.selectCompletionTrend(courseIds), totalAssignments));

        // F-DASH-04 班级知识点掌握度
        vo.setKnowledgeMastery(buildKnowledgeMastery(statMapper.selectKnowledgeMastery(courseIds)));

        // F-DASH-05 问答活跃度（近 7 天）
        vo.setQaActivity(buildQaActivity(statMapper.selectQaActivity(courseIds)));

        // F-DASH-06 学生排行榜
        vo.setStudentRanking(buildStudentRanking(statMapper.selectStudentRanking(courseIds), totalAssignments));

        return vo;
    }

    @Override
    public AnalyticsOverviewVO getAnalyticsOverview(Long studentId) {
        AnalyticsOverviewVO vo = new AnalyticsOverviewVO();

        // F-LEARN-01 概览卡
        AnalyticsOverviewVO.Overview overview = new AnalyticsOverviewVO.Overview();
        Map<String, Object> acc = statMapper.selectStudentAccuracy(studentId);
        long total = acc == null ? 0 : toLong(acc.get("total"));
        long correct = acc == null ? 0 : toLong(acc.get("correct"));
        overview.setAvgAccuracy(total > 0 ? correct * 100.0 / total : 0);
        overview.setActivityCount(statMapper.countStudentActivity(studentId));
        overview.setCourseCount(statMapper.countStudentCourses(studentId));
        overview.setAvgScore(statMapper.selectStudentAvgScore(studentId));
        vo.setOverview(overview);

        // F-LEARN-01 成绩趋势
        vo.setScoreTrend(buildScoreTrend(statMapper.selectStudentScoreTrend(studentId)));

        // F-LEARN-02 知识盲区
        List<AnalyticsOverviewVO.KnowledgeGap> gaps = buildKnowledgeGaps(statMapper.selectStudentKnowledgeGaps(studentId));
        vo.setKnowledgeGaps(gaps);

        // F-LEARN-03 可视化学习路径（根据薄弱知识点生成步骤条）
        vo.setLearningPath(buildLearningPath(gaps));

        // F-LEARN-04 个性化建议（L3 动态生成 + 静态建议合并）
        List<AnalyticsOverviewVO.Suggestion> dbSuggestions = buildSuggestions(
                statMapper.selectStudentSuggestions(studentId, suggestionLimit));
        List<AnalyticsOverviewVO.Suggestion> dynamicSuggestions = buildDynamicSuggestions(gaps);
        // 合并：动态建议在前，静态建议在后
        List<AnalyticsOverviewVO.Suggestion> allSuggestions = new ArrayList<>();
        allSuggestions.addAll(dynamicSuggestions);
        allSuggestions.addAll(dbSuggestions);
        vo.setSuggestions(allSuggestions.subList(0, Math.min(allSuggestions.size(), suggestionLimit)));

        return vo;
    }

    // ===================== 以下为各图表的组装与空数据补齐 =====================

    private DashboardStatVO.Overview buildEmptyOverview() {
        DashboardStatVO.Overview o = new DashboardStatVO.Overview();
        o.setCourseCount(0);
        o.setStudentCount(0);
        o.setAssignmentCount(0);
        o.setQaCount(0);
        return o;
    }

    private List<DashboardStatVO.ScoreRange> buildEmptyScoreDistribution() {
        List<DashboardStatVO.ScoreRange> list = new ArrayList<>();
        for (String r : getScoreRanges()) {
            DashboardStatVO.ScoreRange item = new DashboardStatVO.ScoreRange();
            item.setRange(r);
            item.setCount(0);
            list.add(item);
        }
        return list;
    }

    private List<DashboardStatVO.ScoreRange> buildScoreDistribution(List<Map<String, Object>> rows) {
        Map<String, Long> map = new LinkedHashMap<>();
        for (String r : getScoreRanges()) {
            map.put(r, 0L);
        }
        if (rows != null) {
            for (Map<String, Object> row : rows) {
                String range = String.valueOf(row.get("score_range"));
                map.put(range, toLong(row.get("cnt")));
            }
        }
        List<DashboardStatVO.ScoreRange> list = new ArrayList<>();
        map.forEach((range, cnt) -> {
            DashboardStatVO.ScoreRange item = new DashboardStatVO.ScoreRange();
            item.setRange(range);
            item.setCount(cnt);
            list.add(item);
        });
        return list;
    }

    private List<DashboardStatVO.CompletionTrend> buildCompletionTrend(List<Map<String, Object>> rows, long totalAssignments) {
        List<DashboardStatVO.CompletionTrend> list = new ArrayList<>();
        if (rows == null) {
            return list;
        }
        for (Map<String, Object> row : rows) {
            DashboardStatVO.CompletionTrend item = new DashboardStatVO.CompletionTrend();
            item.setTitle(String.valueOf(row.get("title")));
            long enrolled = toLong(row.get("enrolled"));
            long submitted = toLong(row.get("submitted"));
            item.setCompletionRate(enrolled > 0 ? submitted * 100.0 / enrolled : 0);
            item.setAvgScore(toDouble(row.get("avg_score")));
            list.add(item);
        }
        return list;
    }

    private List<DashboardStatVO.KnowledgeMastery> buildKnowledgeMastery(List<Map<String, Object>> rows) {
        List<DashboardStatVO.KnowledgeMastery> list = new ArrayList<>();
        if (rows == null) {
            return list;
        }
        for (Map<String, Object> row : rows) {
            DashboardStatVO.KnowledgeMastery item = new DashboardStatVO.KnowledgeMastery();
            item.setKnowledgePoint(String.valueOf(row.get("knowledge_point")));
            long total = toLong(row.get("total"));
            long correct = toLong(row.get("correct"));
            item.setMastery(total > 0 ? correct * 100.0 / total : 0);
            list.add(item);
        }
        return list;
    }

    private List<DashboardStatVO.QaActivity> buildQaActivity(List<Map<String, Object>> rows) {
        // 生成近 7 天标签（MM-DD），初始化为 0
        Map<String, Long> map = new LinkedHashMap<>();
        LocalDate today = LocalDate.now();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MM-dd");
        for (int i = 6; i >= 0; i--) {
            map.put(today.minusDays(i).format(fmt), 0L);
        }
        if (rows != null) {
            for (Map<String, Object> row : rows) {
                String label = String.valueOf(row.get("day_label"));
                if (map.containsKey(label)) {
                    map.put(label, toLong(row.get("cnt")));
                }
            }
        }
        List<DashboardStatVO.QaActivity> list = new ArrayList<>();
        map.forEach((label, cnt) -> {
            DashboardStatVO.QaActivity item = new DashboardStatVO.QaActivity();
            item.setDateLabel(label);
            item.setCount(cnt);
            list.add(item);
        });
        return list;
    }

    private List<DashboardStatVO.StudentRank> buildStudentRanking(List<Map<String, Object>> rows, long totalAssignments) {
        List<DashboardStatVO.StudentRank> list = new ArrayList<>();
        if (rows == null) {
            return list;
        }
        for (Map<String, Object> row : rows) {
            DashboardStatVO.StudentRank item = new DashboardStatVO.StudentRank();
            item.setStudentName(String.valueOf(row.get("student_name")));
            item.setAvgScore(toDouble(row.get("avg_score")));
            long submitted = toLong(row.get("submitted_count"));
            double completionRate = totalAssignments > 0 ? submitted * 100.0 / totalAssignments : 0;
            item.setCompletionRate(completionRate);
            // 学情预警（F-LEARN-05）：低分或低完成率
            StringBuilder reason = new StringBuilder();
            if (item.getAvgScore() < WARN_SCORE_THRESHOLD) reason.append("低分");
            if (completionRate < WARN_COMPLETION_THRESHOLD) {
                if (reason.length() > 0) reason.append("、");
                reason.append("低完成率");
            }
            item.setWarning(reason.length() > 0);
            item.setWarningReason(reason.length() > 0 ? reason.toString() : null);
            list.add(item);
        }
        return list;
    }

    private List<AnalyticsOverviewVO.ScoreTrend> buildScoreTrend(List<Map<String, Object>> rows) {
        List<AnalyticsOverviewVO.ScoreTrend> list = new ArrayList<>();
        if (rows == null) {
            return list;
        }
        for (Map<String, Object> row : rows) {
            AnalyticsOverviewVO.ScoreTrend item = new AnalyticsOverviewVO.ScoreTrend();
            item.setTitle(String.valueOf(row.get("title")));
            item.setScore(toDouble(row.get("score")));
            item.setFullScore(toDouble(row.get("full_score")));
            list.add(item);
        }
        return list;
    }

    private List<AnalyticsOverviewVO.KnowledgeGap> buildKnowledgeGaps(List<Map<String, Object>> rows) {
        List<AnalyticsOverviewVO.KnowledgeGap> list = new ArrayList<>();
        if (rows == null) {
            return list;
        }
        for (Map<String, Object> row : rows) {
            AnalyticsOverviewVO.KnowledgeGap item = new AnalyticsOverviewVO.KnowledgeGap();
            item.setKnowledgePoint(String.valueOf(row.get("knowledge_point")));
            long total = toLong(row.get("total"));
            long wrong = toLong(row.get("wrong"));
            double errorRate = total > 0 ? wrong * 100.0 / total : 0;
            item.setErrorRate(errorRate);
            item.setMastery(100 - errorRate);
            item.setWeak(errorRate >= weakThreshold);
            list.add(item);
        }
        return list;
    }

    private List<AnalyticsOverviewVO.Suggestion> buildSuggestions(List<Map<String, Object>> rows) {
        List<AnalyticsOverviewVO.Suggestion> list = new ArrayList<>();
        if (rows == null) {
            return list;
        }
        for (Map<String, Object> row : rows) {
            AnalyticsOverviewVO.Suggestion item = new AnalyticsOverviewVO.Suggestion();
            item.setContent(String.valueOf(row.get("content")));
            item.setType(String.valueOf(row.get("type")));
            list.add(item);
        }
        return list;
    }

    /**
     * L3 根据知识盲区动态生成学习建议
     * <p>规则：薄弱知识点生成"建议复习 XX"类型的建议。</p>
     */
    private List<AnalyticsOverviewVO.Suggestion> buildDynamicSuggestions(List<AnalyticsOverviewVO.KnowledgeGap> gaps) {
        List<AnalyticsOverviewVO.Suggestion> list = new ArrayList<>();
        if (gaps == null || gaps.isEmpty()) {
            return list;
        }
        // 为每个薄弱知识点生成建议
        for (AnalyticsOverviewVO.KnowledgeGap gap : gaps) {
            if (gap.isWeak()) {
                AnalyticsOverviewVO.Suggestion item = new AnalyticsOverviewVO.Suggestion();
                item.setContent("建议重点复习「" + gap.getKnowledgePoint() + "」，当前掌握度仅 " +
                        String.format("%.0f%%", gap.getMastery()) + "，可通过问答或重做相关作业来巩固。");
                item.setType("知识点巩固");
                list.add(item);
            }
        }
        return list;
    }

    /**
     * F-LEARN-03 根据知识盲区生成可视化学习路径步骤
     * <p>规则：薄弱知识点（weak=true）排在前面作为"待完成"步骤，
     * 掌握良好的知识点作为"已完成"步骤，第一个薄弱点标记为"进行中"。</p>
     */
    private List<AnalyticsOverviewVO.LearningStep> buildLearningPath(List<AnalyticsOverviewVO.KnowledgeGap> gaps) {
        List<AnalyticsOverviewVO.LearningStep> steps = new ArrayList<>();
        if (gaps == null || gaps.isEmpty()) {
            return steps;
        }
        // 按掌握度升序排列（最薄弱的排前面）
        List<AnalyticsOverviewVO.KnowledgeGap> sorted = new ArrayList<>(gaps);
        sorted.sort((a, b) -> Double.compare(a.getMastery(), b.getMastery()));

        boolean foundCurrent = false;
        for (AnalyticsOverviewVO.KnowledgeGap gap : sorted) {
            AnalyticsOverviewVO.LearningStep step = new AnalyticsOverviewVO.LearningStep();
            step.setTitle(gap.getKnowledgePoint());
            step.setMastery(gap.getMastery());
            if (gap.isWeak()) {
                if (!foundCurrent) {
                    // 第一个薄弱点标记为"进行中"
                    step.setStatus(1);
                    step.setDescription("建议重点复习「" + gap.getKnowledgePoint() + "」，当前掌握度仅 "
                            + String.format("%.0f", gap.getMastery()) + "%，错误率较高。");
                    foundCurrent = true;
                } else {
                    // 后续薄弱点标记为"待完成"
                    step.setStatus(0);
                    step.setDescription("建议在巩固前面内容后，复习「" + gap.getKnowledgePoint() + "」，掌握度 "
                            + String.format("%.0f", gap.getMastery()) + "%。");
                }
            } else {
                // 掌握良好的知识点标记为"已完成"
                step.setStatus(2);
                step.setDescription("已掌握，可作为复习巩固参考。");
            }
            steps.add(step);
        }
        return steps;
    }

    /** 将可能为 BigInteger/BigDecimal/Long/Double 的查询结果安全转为 long */
    private long toLong(Object v) {
        return v == null ? 0L : ((Number) v).longValue();
    }

    /** 将可能为 BigInteger/BigDecimal/Long/Double 的查询结果安全转为 double */
    private double toDouble(Object v) {
        return v == null ? 0d : ((Number) v).doubleValue();
    }
}
