package com.aetherlearn.service.impl;

import com.aetherlearn.ai.AiConfig;
import com.aetherlearn.ai.LlmClient;
import com.aetherlearn.common.BusinessException;
import com.aetherlearn.common.RoleConstant;
import com.aetherlearn.dto.AnswerSubmitRequest;
import com.aetherlearn.dto.AssignmentDetailVO;
import com.aetherlearn.dto.AssignmentSaveRequest;
import com.aetherlearn.dto.GradeResultVO;
import com.aetherlearn.dto.QuestionSaveRequest;
import com.aetherlearn.dto.ReviewRequest;
import com.aetherlearn.dto.SubmissionSummaryVO;
import com.aetherlearn.entity.Assignment;
import com.aetherlearn.entity.Course;
import com.aetherlearn.entity.LearningRecord;
import com.aetherlearn.entity.Question;
import com.aetherlearn.entity.StudentAnswer;
import com.aetherlearn.entity.SysUser;
import com.aetherlearn.entity.KnowledgeChunk;
import com.aetherlearn.mapper.AssignmentMapper;
import com.aetherlearn.mapper.CourseMapper;
import com.aetherlearn.mapper.KnowledgeChunkMapper;
import com.aetherlearn.mapper.LearningRecordMapper;
import com.aetherlearn.mapper.QuestionMapper;
import com.aetherlearn.mapper.StudentAnswerMapper;
import com.aetherlearn.mapper.SysUserMapper;
import com.aetherlearn.service.AssignmentService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 作业模块服务实现（F-HW 作业模块）
 * <p>核心能力：
 * <ul>
 *   <li>作业/题目教师端 CRUD（软删除、归属校验）；</li>
 *   <li>学生提交作答 → <b>客观题精确比对自动给分</b>（单选/多选/判断/填空）；</li>
 *   <li>主观题（简答）批改：<b>大模型可用时调用 LLM 评分+反馈；不可用时降级为关键词命中+待复核</b>，
 *       始终保证可离线演示；</li>
 *   <li>批改结果查看与教师复核（调分+复核状态流转），并提交/回写 {@code learning_record} 供学情分析。</li>
 * </ul>
 */
@Slf4j
@Service
public class AssignmentServiceImpl implements AssignmentService {

    // ---- 题型常量 ----
    private static final int Q_SINGLE = 1;   // 单选
    private static final int Q_MULTI = 2;    // 多选
    private static final int Q_JUDGE = 3;    // 判断
    private static final int Q_FILL = 4;     // 填空
    private static final int Q_ESSAY = 5;    // 简答（主观题）

    // ---- 批改方式 / 复核状态 ----
    private static final int GRADE_AUTO = 1;     // 自动批改
    private static final int GRADE_MANUAL = 2;   // 人工复核
    private static final int REVIEW_PENDING = 0; // 待复核
    private static final int REVIEW_MODIFIED = 2;// 已修改分数

    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Pattern JSON_OBJ = Pattern.compile("\\{[^}]*\\}", Pattern.DOTALL);

    private final AssignmentMapper assignmentMapper;
    private final QuestionMapper questionMapper;
    private final StudentAnswerMapper studentAnswerMapper;
    private final LearningRecordMapper learningRecordMapper;
    private final CourseMapper courseMapper;
    private final SysUserMapper sysUserMapper;
    private final KnowledgeChunkMapper knowledgeChunkMapper;
    private final AiConfig aiConfig;
    private final LlmClient llmClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AssignmentServiceImpl(AssignmentMapper assignmentMapper, QuestionMapper questionMapper,
                                 StudentAnswerMapper studentAnswerMapper, LearningRecordMapper learningRecordMapper,
                                 CourseMapper courseMapper, SysUserMapper sysUserMapper,
                                 KnowledgeChunkMapper knowledgeChunkMapper,
                                 AiConfig aiConfig, LlmClient llmClient) {
        this.assignmentMapper = assignmentMapper;
        this.questionMapper = questionMapper;
        this.studentAnswerMapper = studentAnswerMapper;
        this.learningRecordMapper = learningRecordMapper;
        this.courseMapper = courseMapper;
        this.sysUserMapper = sysUserMapper;
        this.knowledgeChunkMapper = knowledgeChunkMapper;
        this.aiConfig = aiConfig;
        this.llmClient = llmClient;
    }

    // ============ 作业列表 ============

    @Override
    public List<Assignment> listAssignments(Long userId, Integer role, Long courseId) {
        if (role != null && role == RoleConstant.STUDENT) {
            return assignmentMapper.selectByStudent(userId, courseId);
        }
        // 教师/管理员
        return assignmentMapper.selectByTeacherOrAdmin(userId, role, courseId);
    }

    // ============ 作业保存（教师） ============

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Assignment saveAssignment(AssignmentSaveRequest request, Long operatorId, Integer role) {
        if (request.getCourseId() == null) {
            throw new BusinessException(400, "请选择所属课程");
        }
        validateCourseOwnership(request.getCourseId(), operatorId, role);

        Assignment assignment;
        if (request.getId() != null) {
            // 编辑：校验归属
            assignment = assignmentMapper.selectById(request.getId());
            if (assignment == null) {
                throw new BusinessException(404, "作业不存在");
            }
            if (role == RoleConstant.TEACHER
                    && !Objects.equals(courseTeacherId(assignment.getCourseId()), operatorId)) {
                throw new BusinessException(403, "只能操作本人课程的作业");
            }
        } else {
            assignment = new Assignment();
            assignment.setCreateBy(operatorId);
            assignment.setCreateTime(LocalDateTime.now());
            assignment.setStatus(1);
        }
        assignment.setCourseId(request.getCourseId());
        assignment.setTitle(request.getTitle());
        assignment.setType(request.getType() != null ? request.getType() : 1);
        assignment.setDescription(request.getDescription());
        assignment.setStartTime(parseTime(request.getStartTime()));
        assignment.setEndTime(parseTime(request.getEndTime()));
        assignment.setTotalScore(request.getTotalScore() != null ? request.getTotalScore() : 100);

        if (assignment.getId() == null) {
            assignmentMapper.insert(assignment);
        } else {
            assignmentMapper.updateById(assignment);
        }
        return assignment;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteAssignment(Long id) {
        Assignment assignment = assignmentMapper.selectById(id);
        if (assignment == null) {
            throw new BusinessException(404, "作业不存在或已删除");
        }
        // removeById 配合 @TableLogic 执行软删除
        assignmentMapper.deleteById(id);
    }

    // ============ 作业详情 ============

    @Override
    public AssignmentDetailVO getDetail(Long assignmentId, boolean hideAnswer) {
        Assignment assignment = assignmentMapper.selectById(assignmentId);
        if (assignment == null) {
            throw new BusinessException(404, "作业不存在");
        }
        List<Question> questions = questionMapper.selectByAssignmentId(assignmentId);
        AssignmentDetailVO vo = new AssignmentDetailVO();
        vo.setAssignment(assignment);
        vo.setQuestions(questions.stream().map(q -> {
            AssignmentDetailVO.QuestionVO qv = new AssignmentDetailVO.QuestionVO();
            qv.setId(q.getId());
            qv.setAssignmentId(q.getAssignmentId());
            qv.setType(q.getType());
            qv.setContent(q.getContent());
            qv.setOptions(parseOptions(q.getOptions()));
            qv.setAnswer(hideAnswer ? null : q.getAnswer()); // 学生视角隐藏标准答案
            qv.setAnalysis(q.getAnalysis());
            qv.setScore(q.getScore());
            qv.setKnowledgePoint(q.getKnowledgePoint());
            qv.setSeq(q.getSeq());
            return qv;
        }).collect(Collectors.toList()));
        return vo;
    }

    // ============ 题目保存（教师） ============

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Question saveQuestion(QuestionSaveRequest request) {
        if (request.getAssignmentId() == null) {
            throw new BusinessException(400, "请选择所属作业");
        }
        Assignment assignment = assignmentMapper.selectById(request.getAssignmentId());
        if (assignment == null) {
            throw new BusinessException(404, "作业不存在");
        }
        Question question;
        if (request.getId() != null) {
            question = questionMapper.selectById(request.getId());
            if (question == null) {
                throw new BusinessException(404, "题目不存在");
            }
        } else {
            question = new Question();
            question.setAssignmentId(request.getAssignmentId());
            // 新增题目默认序号为当前题目数+1
            question.setSeq(questionMapper.selectByAssignmentId(request.getAssignmentId()).size() + 1);
        }
        question.setType(request.getType());
        question.setContent(request.getContent());
        question.setOptions(toJson(request.getOptions()));
        question.setAnswer(request.getAnswer());
        question.setAnalysis(request.getAnalysis());
        question.setScore(request.getScore() != null ? request.getScore() : 0);
        question.setKnowledgePoint(request.getKnowledgePoint());
        if (request.getSeq() != null) {
            question.setSeq(request.getSeq());
        }
        if (question.getId() == null) {
            questionMapper.insert(question);
        } else {
            questionMapper.updateById(question);
        }
        return question;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteQuestion(Long id) {
        if (questionMapper.selectById(id) == null) {
            throw new BusinessException(404, "题目不存在");
        }
        questionMapper.deleteById(id);
    }

    // ============ 学生提交 + 自动批改 ============

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GradeResultVO submit(Long studentId, AnswerSubmitRequest request) {
        Long assignmentId = request.getAssignmentId();
        Assignment assignment = assignmentMapper.selectById(assignmentId);
        if (assignment == null) {
            throw new BusinessException(404, "作业不存在");
        }
        // 校验截止时间：过期禁止提交
        if (assignment.getEndTime() != null && LocalDateTime.now().isAfter(assignment.getEndTime())) {
            throw new BusinessException(400, "作业已截止，无法提交");
        }
        List<Question> questions = questionMapper.selectByAssignmentId(assignmentId);
        Map<Long, Question> questionMap = questions.stream()
                .collect(Collectors.toMap(Question::getId, q -> q, (a, b) -> a));

        // 已有作答（用于幂等 upsert）
        Map<Long, StudentAnswer> existing = studentAnswerMapper
                .selectByAssignmentAndStudent(assignmentId, studentId).stream()
                .collect(Collectors.toMap(StudentAnswer::getQuestionId, a -> a, (a, b) -> a));

        List<StudentAnswer> saved = new ArrayList<>();
        for (AnswerSubmitRequest.AnswerItem item : request.getAnswers()) {
            Question q = questionMap.get(item.getQuestionId());
            if (q == null) {
                continue; // 跳过非法题目
            }
            String ans = item.getContent() == null ? "" : item.getContent();
            Graded g = gradeOne(q, ans);

            StudentAnswer sa = existing.get(q.getId());
            boolean isNew = sa == null;
            if (isNew) {
                sa = new StudentAnswer();
                sa.setAssignmentId(assignmentId);
                sa.setQuestionId(q.getId());
                sa.setStudentId(studentId);
                sa.setCreateTime(LocalDateTime.now());
            }
            sa.setAnswer(ans);
            sa.setScore(g.score);
            sa.setIsCorrect(g.correct == null ? null : (g.correct ? 1 : 0));
            sa.setFeedback(g.feedback);
            sa.setGradeType(g.gradeType);
            sa.setReviewStatus(g.reviewStatus);
            // 作答图片（F-HW-04）
            if (item.getImageUrl() != null && !item.getImageUrl().isBlank()) {
                sa.setImageUrl(item.getImageUrl());
            }
            if (isNew) {
                studentAnswerMapper.insert(sa);
            } else {
                studentAnswerMapper.updateById(sa);
            }
            saved.add(sa);
        }

        // 总分并写入学习行为记录（供学情分析）
        int earned = saved.stream().mapToInt(a -> a.getScore() == null ? 0 : a.getScore()).sum();
        syncLearningRecord(studentId, assignment, earned);

        Map<Long, StudentAnswer> savedMap = saved.stream()
                .collect(Collectors.toMap(StudentAnswer::getQuestionId, a -> a, (a, b) -> a));
        return buildResult(assignment, questions, savedMap, true);
    }

    @Override
    public GradeResultVO getResult(Long studentId, Long assignmentId) {
        Assignment assignment = assignmentMapper.selectById(assignmentId);
        if (assignment == null) {
            throw new BusinessException(404, "作业不存在");
        }
        List<Question> questions = questionMapper.selectByAssignmentId(assignmentId);
        List<StudentAnswer> answers = studentAnswerMapper.selectByAssignmentAndStudent(assignmentId, studentId);
        Map<Long, StudentAnswer> answerMap = answers.stream()
                .collect(Collectors.toMap(StudentAnswer::getQuestionId, a -> a, (a, b) -> a));
        return buildResult(assignment, questions, answerMap, !answers.isEmpty());
    }

    // ============ 教师查看提交情况 ============

    @Override
    public List<SubmissionSummaryVO> getSubmissions(Long assignmentId) {
        Assignment assignment = assignmentMapper.selectById(assignmentId);
        if (assignment == null) {
            throw new BusinessException(404, "作业不存在");
        }
        List<Question> questions = questionMapper.selectByAssignmentId(assignmentId);
        int questionCount = questions.size();
        List<Long> studentIds = studentAnswerMapper.selectStudentIdsByAssignment(assignmentId);

        List<SubmissionSummaryVO> result = new ArrayList<>();
        for (Long studentId : studentIds) {
            List<StudentAnswer> answers = studentAnswerMapper.selectByAssignmentAndStudent(assignmentId, studentId);
            int earned = answers.stream().mapToInt(a -> a.getScore() == null ? 0 : a.getScore()).sum();
            long answered = answers.stream().filter(a -> a.getAnswer() != null && !a.getAnswer().isBlank()).count();
            long pending = answers.stream()
                    .filter(a -> GRADE_MANUAL == a.getGradeType() && REVIEW_PENDING == a.getReviewStatus()).count();
            LocalDateTime submitTime = answers.stream()
                    .map(StudentAnswer::getCreateTime).filter(Objects::nonNull)
                    .max(LocalDateTime::compareTo).orElse(null);
            SysUser user = sysUserMapper.selectById(studentId);

            SubmissionSummaryVO vo = new SubmissionSummaryVO();
            vo.setStudentId(studentId);
            vo.setStudentName(user != null ? user.getRealName() : ("学生#" + studentId));
            vo.setEarnedScore(earned);
            vo.setQuestionCount(questionCount);
            vo.setAnsweredCount((int) answered);
            vo.setPendingReview((int) pending);
            vo.setSubmitTime(submitTime);
            result.add(vo);
        }
        return result;
    }

    // ============ 教师复核 ============

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void review(ReviewRequest request) {
        StudentAnswer sa = studentAnswerMapper.selectById(request.getAnswerId());
        if (sa == null) {
            throw new BusinessException(404, "作答记录不存在");
        }
        sa.setScore(request.getScore());
        sa.setFeedback(request.getFeedback());
        sa.setGradeType(GRADE_MANUAL);
        // 复核状态：未指定则默认"已修改分数"
        sa.setReviewStatus(request.getReviewStatus() != null ? request.getReviewStatus() : REVIEW_MODIFIED);
        studentAnswerMapper.updateById(sa);

        // 重新汇总该生该作业总分并回写学习行为记录
        Assignment assignment = assignmentMapper.selectById(sa.getAssignmentId());
        int earned = studentAnswerMapper.selectByAssignmentAndStudent(sa.getAssignmentId(), sa.getStudentId())
                .stream().mapToInt(a -> a.getScore() == null ? 0 : a.getScore()).sum();
        if (assignment != null) {
            syncLearningRecord(sa.getStudentId(), assignment, earned);
        }
    }

    // ================= 私有辅助方法 =================

    /** 单题批改：客观题走精确比对，主观题走 LLM 或关键词降级 */
    private Graded gradeOne(Question q, String ans) {
        int type = q.getType() == null ? Q_SINGLE : q.getType();
        if (type == Q_SINGLE || type == Q_MULTI || type == Q_JUDGE || type == Q_FILL) {
            boolean correct = isObjectiveCorrect(q, ans);
            int full = q.getScore() == null ? 0 : q.getScore();
            int score = correct ? full : 0;
            String feedback = correct ? "回答正确。" : "回答错误，正确答案见下方解析。";
            return new Graded(correct, score, feedback, GRADE_AUTO, REVIEW_PENDING, false);
        }
        // 主观题（简答）
        return gradeSubjective(q, ans);
    }

    /** 客观题正确性判定 */
    private boolean isObjectiveCorrect(Question q, String ans) {
        if (ans == null) return false;
        String std = q.getAnswer() == null ? "" : q.getAnswer().trim();
        String stu = plainAnswerText(ans).trim();
        if (stu.isEmpty()) return false;
        switch (q.getType()) {
            case Q_SINGLE:
            case Q_JUDGE:
                return stu.equalsIgnoreCase(std);
            case Q_MULTI: {
                Set<String> a = toCharSet(std);
                Set<String> b = toCharSet(stu);
                return !a.isEmpty() && a.equals(b);
            }
            case Q_FILL:
                return stu.equalsIgnoreCase(std);
            default:
                return false;
        }
    }

    /** 主观题（简答）批改：优先 LLM，失败降级为关键词命中+待复核 */
    private Graded gradeSubjective(Question q, String ans) {
        int full = q.getScore() == null ? 0 : q.getScore();
        if (ans == null || ans.isBlank()) {
            return new Graded(null, 0, "未作答，已提交教师复核。", GRADE_MANUAL, REVIEW_PENDING, false);
        }
        // 大模型可用时尝试 AI 批改
        if (aiConfig.isAvailable()) {
            String system = "你是 AetherLearn 智能批改助手。请依据标准答案对学生主观题作答进行评分，"
                    + "只输出一个 JSON 对象：{\"score\": 整数(0到满分之间), \"feedback\": \"简短中文反馈\"}，不要输出其它内容。";
            String user = "【题目】" + q.getContent()
                    + "\n【标准答案/要点】" + (q.getAnswer() == null ? "" : q.getAnswer())
                    + "\n【学生作答】" + plainAnswerText(ans)
                    + "\n【满分】" + full;
            String llm = llmClient.chat(system, user);
            if (llm != null) {
                ParsedGrade pg = parseLlmGrade(llm, full);
                if (pg != null) {
                    return new Graded(null, pg.score, pg.feedback, GRADE_MANUAL, REVIEW_PENDING, true);
                }
                log.warn("[批改] LLM 返回无法解析，降级为关键词命中：assignment={}, question={}", q.getAssignmentId(), q.getId());
            }
        }
        // 关键词降级方案（无 Key / LLM 失败）
        KeywordHit kh = keywordHit(q.getAnswer(), plainAnswerText(ans));
        double ratio = kh.total == 0 ? 0.0 : (double) kh.hit / kh.total;
        int score = (int) Math.round(full * ratio);
        String feedback = "AI 批改未开启（无大模型密钥）：命中关键词 " + kh.hit + "/" + kh.total
                + "（" + kh.matched + "）；已提交教师复核，最终分数以教师评定为准。";
        return new Graded(null, score, feedback, GRADE_MANUAL, REVIEW_PENDING, false);
    }

    /** 解析 LLM 返回的评分 JSON（防御式，失败返回 null） */
    private ParsedGrade parseLlmGrade(String text, int full) {
        try {
            Matcher m = JSON_OBJ.matcher(text);
            if (!m.find()) return null;
            JsonNode node = objectMapper.readTree(m.group());
            if (!node.has("score")) return null;
            int score = node.path("score").asInt();
            if (score < 0) score = 0;
            if (score > full) score = full;
            String feedback = node.path("feedback").asText();
            if (feedback == null || feedback.isBlank()) feedback = "已通过 AI 批改。";
            return new ParsedGrade(score, feedback);
        } catch (Exception e) {
            log.warn("[批改] 解析 LLM 评分 JSON 失败：{}", e.getMessage());
            return null;
        }
    }

    /** 关键词命中统计（中文按常见分隔符切分） */
    private KeywordHit keywordHit(String standard, String student) {
        if (standard == null || standard.isBlank()) {
            return new KeywordHit(0, 0, "");
        }
        String[] parts = standard.split("[,，、；;。.\\n/]+");
        List<String> keywords = Arrays.stream(parts)
                .map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList());
        if (keywords.isEmpty()) {
            return new KeywordHit(0, 0, "");
        }
        StringBuilder matched = new StringBuilder();
        int hit = 0;
        for (String kw : keywords) {
            if (student != null && student.contains(kw)) {
                hit++;
                if (matched.length() > 0) matched.append("、");
                matched.append(kw);
            }
        }
        double ratio = (double) hit / keywords.size();
        return new KeywordHit(hit, keywords.size(), matched.toString());
    }

    /** 将作答聚合为批改结果视图 */
    private GradeResultVO buildResult(Assignment assignment, List<Question> questions,
                                      Map<Long, StudentAnswer> answerMap, boolean submitted) {
        GradeResultVO vo = new GradeResultVO();
        vo.setAssignmentId(assignment.getId());
        vo.setAssignmentTitle(assignment.getTitle());
        vo.setTotalScore(assignment.getTotalScore() != null ? assignment.getTotalScore() : 100);
        vo.setSubmitted(submitted);

        int earned = answerMap.values().stream()
                .mapToInt(a -> a.getScore() == null ? 0 : a.getScore()).sum();
        vo.setEarnedScore(earned);

        List<GradeResultVO.GradeItemVO> items = new ArrayList<>();
        for (Question q : questions) {
            GradeResultVO.GradeItemVO item = new GradeResultVO.GradeItemVO();
            StudentAnswer sa = answerMap.get(q.getId());
            item.setQuestionId(q.getId());
            item.setAnswerId(sa != null ? sa.getId() : null);
            item.setType(q.getType());
            item.setContent(q.getContent());
            item.setOptions(parseOptions(q.getOptions()));
            item.setStandardAnswer(q.getAnswer());
            item.setAnalysis(q.getAnalysis());

            if (sa != null) {
                item.setYourAnswer(sa.getAnswer());
                item.setImageUrl(sa.getImageUrl());
                item.setScore(sa.getScore());
                item.setCorrect(sa.getIsCorrect() == null ? null : sa.getIsCorrect() == 1);
                item.setFeedback(sa.getFeedback());
                item.setGradeType(sa.getGradeType());
                item.setReviewStatus(sa.getReviewStatus());
                item.setAiGraded(GRADE_MANUAL == sa.getGradeType());
            } else {
                item.setYourAnswer(null);
                item.setScore(0);
                item.setCorrect(null);
                item.setFeedback(null);
                item.setGradeType(null);
                item.setReviewStatus(null);
                item.setAiGraded(false);
            }
            items.add(item);
        }
        vo.setItems(items);
        return vo;
    }

    /** 写入/更新学习行为记录（作业总分），供学情分析（第四波看板）使用 */
    private void syncLearningRecord(Long studentId, Assignment assignment, int earned) {
        LearningRecord rec = learningRecordMapper.selectHomeworkRecord(studentId, assignment.getId());
        if (rec == null) {
            rec = new LearningRecord();
            rec.setStudentId(studentId);
            rec.setCourseId(assignment.getCourseId());
            rec.setActionType("作业");
            rec.setTargetId(assignment.getId());
            rec.setCreateTime(LocalDateTime.now());
        }
        rec.setScore(earned);
        if (rec.getId() == null) {
            learningRecordMapper.insert(rec);
        } else {
            learningRecordMapper.updateById(rec);
        }
    }

    /** 校验课程归属：教师只能操作本人课程 */
    private void validateCourseOwnership(Long courseId, Long operatorId, Integer role) {
        if (role == RoleConstant.TEACHER) {
            Long teacherId = courseTeacherId(courseId);
            if (teacherId == null || !teacherId.equals(operatorId)) {
                throw new BusinessException(403, "只能操作本人课程的作业");
            }
        }
    }

    private Long courseTeacherId(Long courseId) {
        Course course = courseMapper.selectById(courseId);
        return course == null ? null : course.getTeacherId();
    }

    /**
     * 解析作业起止时间，兼容标准日期字符串和旧前端传来的毫秒时间戳。
     */
    private LocalDateTime parseTime(String time) {
        if (time == null || time.isBlank()) return null;
        try {
            String normalized = time.trim();
            if (normalized.matches("\\d{13}")) {
                return LocalDateTime.ofInstant(Instant.ofEpochMilli(Long.parseLong(normalized)), ZoneId.systemDefault());
            }
            return LocalDateTime.parse(normalized, DTF);
        } catch (Exception e) {
            throw new BusinessException(400, "时间格式应为 yyyy-MM-dd HH:mm:ss");
        }
    }

    private String toJson(List<String> list) {
        if (list == null) return "[]";
        try {
            return objectMapper.writeValueAsString(list);
        } catch (Exception e) {
            return "[]";
        }
    }

    /** 将富文本作答转为纯文本，供填空题精确比对与主观题 AI 批改使用。 */
    private String plainAnswerText(String answer) {
        if (answer == null) return "";
        return answer.replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)</p>", "\n")
                .replaceAll("<[^>]+>", "")
                .replace("&nbsp;", " ")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&")
                .trim();
    }

    @SuppressWarnings("unchecked")
    private List<String> parseOptions(String json) {
        if (json == null || json.isBlank()) return new ArrayList<>();
        try {
            return objectMapper.readValue(json, List.class);
        } catch (Exception e) {
            return normalizeOptionText(json);
        }
    }

    /**
     * 兼容 AI 旧格式选项：大模型可能返回 "A.选项1|B.选项2" 或带换行的文本，
     * 这里统一清洗为前端可渲染的选项内容列表。
     */
    private List<String> normalizeOptionText(String raw) {
        if (raw == null || raw.isBlank()) return new ArrayList<>();
        String normalized = raw.replace("\r", "\n")
                .replace("；", "|")
                .replace(";", "|")
                .replace("｜", "|");
        if (!normalized.contains("|")) {
            normalized = normalized.replaceAll("\\n+", "|");
        }
        String[] parts = normalized.split("\\|");
        List<String> options = new ArrayList<>();
        for (String part : parts) {
            String option = part == null ? "" : part.trim();
            if (option.isBlank()) continue;
            option = option.replaceFirst("^[A-Ha-h][\\.．、:)：\\s]+", "").trim();
            option = option.replaceAll("^\"|\"$", "").trim();
            if (!option.isBlank()) {
                options.add(option);
            }
        }
        return options;
    }

    /** 将 AI 生成的选择题选项统一转为 JSON 数组字符串，避免前端无法解析。 */
    private String generatedOptionsToJson(JsonNode node, int questionType) {
        if (questionType != Q_SINGLE && questionType != Q_MULTI) {
            return "[]";
        }
        List<String> options = new ArrayList<>();
        JsonNode optionNode = node.get("options");
        if (optionNode != null && optionNode.isArray()) {
            for (JsonNode item : optionNode) {
                if (item != null && !item.asText("").isBlank()) {
                    options.add(item.asText().replaceFirst("^[A-Ha-h][\\.．、:)：\\s]+", "").trim());
                }
            }
        } else if (optionNode != null) {
            options.addAll(normalizeOptionText(optionNode.asText()));
        }
        if (options.size() < 2) {
            throw new BusinessException(500, "AI 返回的选择题缺少选项，请重新生成");
        }
        return toJson(options);
    }

    /** 规范 AI 生成的标准答案：选择题只保留 A/B/C/D 等答案字母。 */
    private String normalizeGeneratedAnswer(String answer, int questionType) {
        if (answer == null) return "";
        String trimmed = answer.trim();
        if (questionType == Q_SINGLE) {
            Matcher matcher = Pattern.compile("[A-Ha-h]").matcher(trimmed);
            return matcher.find() ? matcher.group().toUpperCase() : trimmed;
        }
        if (questionType == Q_MULTI) {
            StringBuilder sb = new StringBuilder();
            Matcher matcher = Pattern.compile("[A-Ha-h]").matcher(trimmed);
            while (matcher.find()) {
                String letter = matcher.group().toUpperCase();
                if (sb.indexOf(letter) < 0) {
                    sb.append(letter);
                }
            }
            return !sb.isEmpty() ? sb.toString() : trimmed;
        }
        return trimmed;
    }

    /** 单题批改结果临时载体 */
    private static class Graded {
        final Boolean correct;     // 是否正确（客观题）；主观题为 null
        final int score;           // 本题得分
        final String feedback;     // 反馈
        final int gradeType;       // 1-自动 2-人工
        final int reviewStatus;    // 0-待复核
        final boolean aiGraded;    // 是否经 AI 批改

        Graded(Boolean correct, int score, String feedback, int gradeType, int reviewStatus, boolean aiGraded) {
            this.correct = correct;
            this.score = score;
            this.feedback = feedback;
            this.gradeType = gradeType;
            this.reviewStatus = reviewStatus;
            this.aiGraded = aiGraded;
        }
    }

    /** LLM 解析出的评分 */
    private static class ParsedGrade {
        final int score;
        final String feedback;
        ParsedGrade(int score, String feedback) {
            this.score = score;
            this.feedback = feedback;
        }
    }

    /** 关键词命中统计结果 */
    private static class KeywordHit {
        final int hit;
        final int total;
        final String matched;
        KeywordHit(int hit, int total, String matched) {
            this.hit = hit;
            this.total = total;
            this.matched = matched;
        }
    }

    /** 将多选题答案拆分为单字符集合（如 "ABC" -> {A,B,C}） */
    private static Set<String> toCharSet(String s) {
        Set<String> set = new HashSet<>();
        if (s == null) return set;
        for (char c : s.toCharArray()) {
            if (Character.isLetterOrDigit(c)) {
                set.add(String.valueOf(Character.toUpperCase(c)));
            }
        }
        return set;
    }

    // ============ L1 自动出题 ============

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<Question> autoGenerateQuestions(Long assignmentId, Long courseId, int count, int questionType) {
        // 校验作业是否存在
        Assignment assignment = assignmentMapper.selectById(assignmentId);
        if (assignment == null) {
            throw new BusinessException(404, "作业不存在");
        }
        // 校验 LLM 是否可用
        if (!aiConfig.isAvailable()) {
            throw new BusinessException(400, "AI 模型未配置，无法自动出题");
        }
        // 从知识库加载切片作为出题素材
        List<KnowledgeChunk> chunks = knowledgeChunkMapper.selectByCourseId(courseId);
        if (chunks.isEmpty()) {
            throw new BusinessException(400, "该课程知识库为空，请先上传课程资料");
        }
        // 拼接知识库内容（最多取前 3000 字）
        StringBuilder context = new StringBuilder();
        for (KnowledgeChunk c : chunks) {
            if (context.length() + c.getContent().length() > 3000) break;
            context.append(c.getContent()).append("\n\n");
        }

        // 构建 prompt
        String typeName = switch (questionType) {
            case Q_SINGLE -> "单选题（4个选项A/B/C/D，只有一个正确答案）";
            case Q_MULTI -> "多选题（4个选项A/B/C/D，有多个正确答案）";
            case Q_JUDGE -> "判断题（答案为'对'或'错'）";
            case Q_FILL -> "填空题（用___表示空格，给出标准答案）";
            case Q_ESSAY -> "简答题（给出参考答案要点）";
            default -> throw new BusinessException(400, "不支持的题型：" + questionType);
        };

        String system = "你是 AetherLearn 智能出题助手。请依据下方【知识库内容】生成指定数量和类型的题目。"
                + "每道题输出一个 JSON 对象，所有题目用 JSON 数组返回。格式：\n"
                + "[{\"content\":\"题目内容\",\"options\":[\"选项1\",\"选项2\",\"选项3\",\"选项4\"],\"answer\":\"A\",\"analysis\":\"解析\",\"knowledgePoint\":\"知识点\",\"score\":5}]\n"
                + "硬性要求：单选题和多选题必须给出 4 个 options，options 必须是 JSON 数组，不要带 A/B/C/D 前缀；"
                + "单选答案只写一个字母如 A，多选答案写多个字母如 AC；判断/填空/简答 options 使用空数组；"
                + "填空题题干用 ___ 表示空格；简答或分析类题目给出参考答案要点；score 默认5分；只输出 JSON 数组，不要其它内容。";

        String user = "【知识库内容】\n" + context
                + "\n\n【出题要求】\n题型：" + typeName
                + "\n数量：" + count + "道";

        String llmResponse = llmClient.chat(system, user);
        if (llmResponse == null || llmResponse.isBlank()) {
            throw new BusinessException(500, "AI 出题失败，请稍后重试");
        }

        // 解析 LLM 返回的 JSON
        List<Question> questions = new ArrayList<>();
        try {
            // 提取 JSON 数组（可能被 markdown 代码块包裹）
            String json = llmResponse.trim();
            if (json.contains("```")) {
                json = json.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
            }
            // 确保是数组格式
            if (!json.startsWith("[")) {
                // 尝试提取第一个 [ 到最后一个 ]
                int start = json.indexOf('[');
                int end = json.lastIndexOf(']');
                if (start >= 0 && end > start) {
                    json = json.substring(start, end + 1);
                }
            }

            JsonNode array = objectMapper.readTree(json);
            if (!array.isArray()) {
                throw new BusinessException(500, "AI 返回格式错误");
            }

            // 获取当前作业已有题目的最大序号
            int maxSeq = 0;
            List<Question> existing = questionMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Question>()
                            .eq(Question::getAssignmentId, assignmentId));
            for (Question q : existing) {
                if (q.getSeq() != null && q.getSeq() > maxSeq) maxSeq = q.getSeq();
            }

            for (JsonNode node : array) {
                Question q = new Question();
                q.setAssignmentId(assignmentId);
                q.setType(questionType);
                q.setContent(node.has("content") ? node.get("content").asText() : "");
                q.setOptions(generatedOptionsToJson(node, questionType));
                q.setAnswer(normalizeGeneratedAnswer(node.has("answer") ? node.get("answer").asText() : "", questionType));
                q.setAnalysis(node.has("analysis") ? node.get("analysis").asText() : "");
                q.setKnowledgePoint(node.has("knowledgePoint") ? node.get("knowledgePoint").asText() : "");
                q.setScore(node.has("score") ? node.get("score").asInt(5) : 5);
                q.setSeq(++maxSeq);
                questionMapper.insert(q);
                questions.add(q);
            }
            log.info("[作业] AI 自动出题完成：assignmentId={}, 生成{}道{}", assignmentId, questions.size(), typeName);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("[作业] AI 出题解析失败", e);
            throw new BusinessException(500, "AI 返回内容解析失败：" + e.getMessage());
        }
        return questions;
    }
}
