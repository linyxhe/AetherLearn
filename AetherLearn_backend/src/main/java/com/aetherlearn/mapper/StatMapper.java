package com.aetherlearn.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 统计聚合 Mapper（F-DASH / F-LEARN 看板与学情模块）
 * <p>看板/学情所需的多维聚合查询集中在此，均按"作用域课程"隔离：
 * 管理员可见全部课程，教师仅可见本人授课课程。</p>
 */
@Mapper
public interface StatMapper {

    /**
     * 取作用域内的课程ID列表（用于后续所有按课程隔离的统计）
     * <p>管理员（role=1）取全部未删课程；教师仅取本人课程。</p>
     */
    @Select("<script>" +
            "SELECT id FROM course WHERE is_deleted = 0 " +
            "<if test='role != 1'> AND teacher_id = #{userId} </if>" +
            "</script>")
    List<Long> selectVisibleCourseIds(@Param("userId") Long userId, @Param("role") Integer role);

    /**
     * 作用域内选课学生总数（去重）
     */
    @Select("<script>" +
            "SELECT COUNT(DISTINCT cs.student_id) FROM course_student cs " +
            "WHERE cs.course_id IN " +
            "<foreach collection='courseIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>" +
            "</script>")
    long countStudents(@Param("courseIds") List<Long> courseIds);

    /**
     * 作用域内作业总数（未删除）
     */
    @Select("<script>" +
            "SELECT COUNT(*) FROM assignment a " +
            "WHERE a.is_deleted = 0 AND a.course_id IN " +
            "<foreach collection='courseIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>" +
            "</script>")
    long countAssignments(@Param("courseIds") List<Long> courseIds);

    /**
     * 作用域内问答总数
     */
    @Select("<script>" +
            "SELECT COUNT(*) FROM qa_record q " +
            "WHERE q.course_id IN " +
            "<foreach collection='courseIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>" +
            "</script>")
    long countQa(@Param("courseIds") List<Long> courseIds);

    /**
     * 成绩分布（F-DASH-02）：按 (作业,学生) 汇总总分后分 5 个分数段统计人数。
     * 返回 Map 键：score_range（0-59/60-69/70-79/80-89/90-100）、cnt。
     */
    @Select("<script>" +
            "SELECT " +
            "  CASE " +
            "    WHEN t.total_score &lt; 60 THEN '0-59' " +
            "    WHEN t.total_score &lt; 70 THEN '60-69' " +
            "    WHEN t.total_score &lt; 80 THEN '70-79' " +
            "    WHEN t.total_score &lt; 90 THEN '80-89' " +
            "    ELSE '90-100' END AS score_range, " +
            "  COUNT(*) AS cnt " +
            "FROM ( " +
            "  SELECT sa.assignment_id, sa.student_id, SUM(sa.score) AS total_score " +
            "  FROM student_answer sa " +
            "  JOIN assignment a ON a.id = sa.assignment_id AND a.is_deleted = 0 " +
            "  WHERE a.course_id IN " +
            "    <foreach collection='courseIds' item='id' open='(' separator=',' close=')'>#{id}</foreach> " +
            "  GROUP BY sa.assignment_id, sa.student_id " +
            ") t " +
            "GROUP BY score_range ORDER BY score_range" +
            "</script>")
    List<Map<String, Object>> selectScoreDistribution(@Param("courseIds") List<Long> courseIds);

    /**
     * 作业完成率趋势（F-DASH-03）：历次作业的提交人数、应提交人数、平均分。
     * 返回 Map 键：assignment_id、title、create_time、submitted、enrolled、avg_score。
     */
    @Select("<script>" +
            "SELECT a.id AS assignment_id, a.title AS title, a.create_time AS create_time, " +
            "       COALESCE(sub.submitted,0) AS submitted, " +
            "       COALESCE(sub.avg_score,0) AS avg_score, " +
            "       COALESCE(enr.enrolled,0) AS enrolled " +
            "FROM assignment a " +
            "LEFT JOIN ( " +
            "  SELECT assignment_id, COUNT(DISTINCT student_id) AS submitted, AVG(total) AS avg_score " +
            "  FROM (SELECT assignment_id, student_id, SUM(score) total FROM student_answer GROUP BY assignment_id, student_id) t " +
            "  GROUP BY assignment_id " +
            ") sub ON sub.assignment_id = a.id " +
            "LEFT JOIN (SELECT course_id, COUNT(DISTINCT student_id) AS enrolled FROM course_student GROUP BY course_id) enr " +
            "  ON enr.course_id = a.course_id " +
            "WHERE a.is_deleted = 0 AND a.course_id IN " +
            "  <foreach collection='courseIds' item='id' open='(' separator=',' close=')'>#{id}</foreach> " +
            "ORDER BY a.create_time ASC" +
            "</script>")
    List<Map<String, Object>> selectCompletionTrend(@Param("courseIds") List<Long> courseIds);

    /**
     * 班级知识点掌握度（F-DASH-04）：按题目关联知识点聚合正确率。
     * 返回 Map 键：knowledge_point、correct、total。
     */
    @Select("<script>" +
            "SELECT q.knowledge_point AS knowledge_point, " +
            "       SUM(CASE WHEN sa.is_correct = 1 THEN 1 ELSE 0 END) AS correct, " +
            "       COUNT(*) AS total " +
            "FROM student_answer sa " +
            "JOIN question q ON q.id = sa.question_id " +
            "JOIN assignment a ON a.id = sa.assignment_id AND a.is_deleted = 0 " +
            "WHERE a.course_id IN " +
            "  <foreach collection='courseIds' item='id' open='(' separator=',' close=')'>#{id}</foreach> " +
            "  AND q.knowledge_point IS NOT NULL AND sa.is_correct IS NOT NULL " +
            "GROUP BY q.knowledge_point ORDER BY total DESC" +
            "</script>")
    List<Map<String, Object>> selectKnowledgeMastery(@Param("courseIds") List<Long> courseIds);

    /**
     * 问答活跃度（F-DASH-05）：近 7 天每日问答量（按作用域课程）。
     * 返回 Map 键：day_label（MM-DD）、cnt。
     */
    @Select("<script>" +
            "SELECT DATE_FORMAT(create_time,'%m-%d') AS day_label, COUNT(*) AS cnt " +
            "FROM qa_record " +
            "WHERE course_id IN " +
            "  <foreach collection='courseIds' item='id' open='(' separator=',' close=')'>#{id}</foreach> " +
            "  AND create_time &gt;= DATE_SUB(CURDATE(), INTERVAL 6 DAY) " +
            "GROUP BY DATE_FORMAT(create_time,'%m-%d')" +
            "</script>")
    List<Map<String, Object>> selectQaActivity(@Param("courseIds") List<Long> courseIds);

    /**
     * 学生排行榜（F-DASH-06）：按学生平均总分降序取 Top10。
     * 返回 Map 键：student_name、avg_score、submitted_count。
     */
    @Select("<script>" +
            "SELECT u.real_name AS student_name, AVG(t.total) AS avg_score, COUNT(DISTINCT t.assignment_id) AS submitted_count " +
            "FROM ( " +
            "  SELECT sa.assignment_id, sa.student_id, SUM(sa.score) AS total " +
            "  FROM student_answer sa " +
            "  JOIN assignment a ON a.id = sa.assignment_id AND a.is_deleted = 0 " +
            "  WHERE a.course_id IN " +
            "    <foreach collection='courseIds' item='id' open='(' separator=',' close=')'>#{id}</foreach> " +
            "  GROUP BY sa.assignment_id, sa.student_id " +
            ") t " +
            "JOIN sys_user u ON u.id = t.student_id " +
            "GROUP BY t.student_id, u.real_name ORDER BY avg_score DESC LIMIT 10" +
            "</script>")
    List<Map<String, Object>> selectStudentRanking(@Param("courseIds") List<Long> courseIds);

    /**
     * 学生整体正确率（F-LEARN-01）：已批改作答中正确数 / 总数。
     * 返回 Map 键：total、correct。
     */
    @Select("SELECT COUNT(*) AS total, " +
            "       SUM(CASE WHEN is_correct = 1 THEN 1 ELSE 0 END) AS correct " +
            "FROM student_answer sa " +
            "JOIN assignment a ON a.id = sa.assignment_id AND a.is_deleted = 0 " +
            "WHERE sa.student_id = #{studentId} AND sa.is_correct IS NOT NULL")
    Map<String, Object> selectStudentAccuracy(@Param("studentId") Long studentId);

    /**
     * 学生学习行为记录数（F-LEARN-01 活跃度）
     */
    @Select("SELECT COUNT(*) FROM learning_record WHERE student_id = #{studentId}")
    long countStudentActivity(@Param("studentId") Long studentId);

    /**
     * 学生已加入课程数（F-LEARN-01）
     */
    @Select("SELECT COUNT(*) FROM course_student WHERE student_id = #{studentId}")
    long countStudentCourses(@Param("studentId") Long studentId);

    /**
     * 学生平均作业总分（F-LEARN-01）
     */
    @Select("SELECT COALESCE(AVG(total),0) FROM " +
            "(SELECT assignment_id, SUM(score) total FROM student_answer " +
            " WHERE student_id = #{studentId} GROUP BY assignment_id) t")
    double selectStudentAvgScore(@Param("studentId") Long studentId);

    /**
     * 学生成绩趋势（F-LEARN-01）：每次作业的总分与满分。
     * 返回 Map 键：title、score、full_score、create_time。
     */
    @Select("SELECT a.title AS title, a.total_score AS full_score, t.total AS score, a.create_time AS create_time " +
            "FROM (SELECT assignment_id, SUM(score) total FROM student_answer " +
            "      WHERE student_id = #{studentId} GROUP BY assignment_id) t " +
            "JOIN assignment a ON a.id = t.assignment_id AND a.is_deleted = 0 " +
            "ORDER BY a.create_time ASC")
    List<Map<String, Object>> selectStudentScoreTrend(@Param("studentId") Long studentId);

    /**
     * 学生知识盲区（F-LEARN-02）：按知识点统计错误率。
     * 返回 Map 键：knowledge_point、total、wrong。
     */
    @Select("SELECT q.knowledge_point AS knowledge_point, COUNT(*) AS total, " +
            "       SUM(CASE WHEN sa.is_correct = 0 THEN 1 ELSE 0 END) AS wrong " +
            "FROM student_answer sa " +
            "JOIN question q ON q.id = sa.question_id " +
            "JOIN assignment a ON a.id = sa.assignment_id AND a.is_deleted = 0 " +
            "WHERE sa.student_id = #{studentId} AND sa.is_correct IS NOT NULL " +
            "GROUP BY q.knowledge_point")
    List<Map<String, Object>> selectStudentKnowledgeGaps(@Param("studentId") Long studentId);

    /**
     * 学生个性化学习建议（F-LEARN-04）：读 learning_suggestion 表。
     * 返回 Map 键：content、type。
     * L4 支持动态 LIMIT。
     */
    @Select("SELECT content, type FROM learning_suggestion " +
            "WHERE student_id = #{studentId} ORDER BY create_time DESC LIMIT #{limit}")
    List<Map<String, Object>> selectStudentSuggestions(@Param("studentId") Long studentId,
                                                       @Param("limit") int limit);
}
