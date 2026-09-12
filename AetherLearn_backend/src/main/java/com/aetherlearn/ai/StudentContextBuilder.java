package com.aetherlearn.ai;

import com.aetherlearn.entity.Course;
import com.aetherlearn.mapper.CourseMapper;
import com.aetherlearn.mapper.StatMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 学情快照构建器（供 Python 侧 ReAct 智能助教的 get_learning_progress 工具使用）。
 *
 * <p>设计取舍——**为什么是"Java 预取快照"而不是让 Python 回调 Java 查询**：</p>
 * <ul>
 *   <li>业务数据访问与鉴权归 Java，快照由 Java 生成，这条边界没有因为引入 Agent 而移动；</li>
 *   <li>避免 {@code Java → Python → Java} 的嵌套调用：嵌套超时是最难排查的一类线上问题，
 *       而收益只是"数据更新鲜"，对本场景（同一轮问答内）毫无意义；</li>
 *   <li>快照是普通 Map，可 JSON 序列化，随请求体下发，Python 侧不需要额外依赖。</li>
 * </ul>
 *
 * <p>所有查询都带 {@code course_id}：问答发生在某门课的语境里，用学生级聚合会把别的课程的
 * 成绩答进当前课程。任何一步查询失败都只记日志并跳过该字段，绝不阻断问答。</p>
 */
@Slf4j
@Component
public class StudentContextBuilder {

    /** 最近成绩条数：够模型判断趋势，又不至于把提示词撑满。 */
    private static final int RECENT_SCORE_LIMIT = 3;
    /** 薄弱知识点条数。 */
    private static final int WEAK_POINT_LIMIT = 3;
    /** 知识点被判定为"薄弱"的错误率阈值（%）。 */
    private static final double WEAK_ERROR_RATE = 40.0;

    private final StatMapper statMapper;
    private final CourseMapper courseMapper;

    public StudentContextBuilder(StatMapper statMapper, CourseMapper courseMapper) {
        this.statMapper = statMapper;
        this.courseMapper = courseMapper;
    }

    /**
     * 构建学情快照。
     *
     * @param studentId 学生ID（由鉴权后的调用方传入，模型不可见亦不可改）
     * @param courseId  课程ID
     * @return 快照 Map；无任何可用数据时返回 null（Python 侧据此不提供学情工具）
     */
    public Map<String, Object> build(Long studentId, Long courseId) {
        if (studentId == null || courseId == null) {
            return null;
        }
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("course_name", courseName(courseId));
        context.put("avg_accuracy", round1(accuracy(studentId, courseId)));
        context.put("activity_count", safeLong(() -> statMapper.countStudentCourseActivity(studentId, courseId)));
        context.put("wrong_count", safeLong(() -> statMapper.countStudentCourseWrongAnswers(studentId, courseId)));
        context.put("pending_todos", safeLong(() -> statMapper.countPendingTodosInCourse(studentId, courseId)));
        context.put("recent_scores", recentScores(studentId, courseId));
        context.put("weak_points", weakPoints(studentId, courseId));
        return context;
    }

    /** 课程名：让模型知道自己在回答哪门课，避免答成别门课。 */
    private String courseName(Long courseId) {
        try {
            Course course = courseMapper.selectById(courseId);
            return course != null ? course.getCourseName() : null;
        } catch (RuntimeException ex) {
            log.warn("[学情快照] 读取课程 {} 失败：{}", courseId, ex.getMessage());
            return null;
        }
    }

    /** 正确率（%）。 */
    private double accuracy(Long studentId, Long courseId) {
        try {
            Map<String, Object> row = statMapper.selectStudentCourseAccuracy(studentId, courseId);
            long total = toLong(row == null ? null : row.get("total"));
            if (total <= 0) {
                return 0;
            }
            return toLong(row.get("correct")) * 100.0 / total;
        } catch (RuntimeException ex) {
            log.warn("[学情快照] 读取正确率失败：{}", ex.getMessage());
            return 0;
        }
    }

    /** 最近几次作业成绩（时间正序，便于模型讲"进步/退步"）。 */
    private List<Map<String, Object>> recentScores(Long studentId, Long courseId) {
        List<Map<String, Object>> result = new ArrayList<>();
        try {
            List<Map<String, Object>> rows =
                    statMapper.selectStudentCourseScoreTrend(studentId, courseId, RECENT_SCORE_LIMIT);
            if (rows == null) {
                return result;
            }
            // 查询按时间倒序取最近 N 次，这里翻正序，模型看到的就是真实先后顺序
            for (int i = rows.size() - 1; i >= 0; i--) {
                Map<String, Object> row = rows.get(i);
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("title", row.get("title"));
                item.put("score", round1(toDouble(row.get("score"))));
                item.put("full_score", round1(toDouble(row.get("full_score"))));
                result.add(item);
            }
        } catch (RuntimeException ex) {
            log.warn("[学情快照] 读取成绩趋势失败：{}", ex.getMessage());
        }
        return result;
    }

    /** 薄弱知识点（错误率从高到低，最多 3 条）。 */
    private List<Map<String, Object>> weakPoints(Long studentId, Long courseId) {
        List<Map<String, Object>> result = new ArrayList<>();
        try {
            List<Map<String, Object>> rows = statMapper.selectStudentCourseKnowledgeGaps(studentId, courseId);
            if (rows == null) {
                return result;
            }
            rows.stream()
                    .map(row -> {
                        long total = toLong(row.get("total"));
                        double errorRate = total <= 0 ? 0 : toLong(row.get("wrong")) * 100.0 / total;
                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("knowledge_point", row.get("knowledge_point"));
                        item.put("error_rate", round1(errorRate));
                        return item;
                    })
                    // 只保留真正薄弱的：否则会把"只错过一次"的知识点也报成薄弱项
                    .filter(item -> toDouble(item.get("error_rate")) >= WEAK_ERROR_RATE)
                    .sorted((a, b) -> Double.compare(toDouble(b.get("error_rate")), toDouble(a.get("error_rate"))))
                    .limit(WEAK_POINT_LIMIT)
                    .forEach(result::add);
        } catch (RuntimeException ex) {
            log.warn("[学情快照] 读取知识盲区失败：{}", ex.getMessage());
        }
        return result;
    }

    /** 单个统计项的容错执行：失败返回 0，不让一个查询拖垮整份快照。 */
    private long safeLong(java.util.function.Supplier<Long> supplier) {
        try {
            Long value = supplier.get();
            return value == null ? 0 : value;
        } catch (RuntimeException ex) {
            log.warn("[学情快照] 统计查询失败：{}", ex.getMessage());
            return 0;
        }
    }

    /** 保留一位小数，避免把一长串浮点数塞给模型。 */
    private double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private long toLong(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private double toDouble(Object value) {
        return value instanceof Number number ? number.doubleValue() : 0.0;
    }
}
