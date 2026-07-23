package com.aetherlearn.service.impl;

import com.aetherlearn.common.BusinessException;
import com.aetherlearn.common.RoleConstant;
import com.aetherlearn.dto.ChapterQuizSaveRequest;
import com.aetherlearn.dto.ChapterQuizSubmitRequest;
import com.aetherlearn.entity.ChapterQuiz;
import com.aetherlearn.entity.ChapterQuizRecord;
import com.aetherlearn.entity.CourseChapter;
import com.aetherlearn.entity.LearningRecord;
import com.aetherlearn.mapper.ChapterQuizMapper;
import com.aetherlearn.mapper.ChapterQuizRecordMapper;
import com.aetherlearn.mapper.CourseChapterMapper;
import com.aetherlearn.mapper.CourseStudentMapper;
import com.aetherlearn.mapper.LearningRecordMapper;
import com.aetherlearn.service.ChapterQuizService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 章节小测服务实现
 * <p>面向章节学习场景，只支持可即时判分的客观题，错题由 {@code chapter_quiz_record.is_correct=0} 沉淀。</p>
 */
@Service
public class ChapterQuizServiceImpl implements ChapterQuizService {

    private final ChapterQuizMapper chapterQuizMapper;
    private final ChapterQuizRecordMapper chapterQuizRecordMapper;
    private final CourseChapterMapper courseChapterMapper;
    private final CourseStudentMapper courseStudentMapper;
    private final LearningRecordMapper learningRecordMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ChapterQuizServiceImpl(ChapterQuizMapper chapterQuizMapper,
                                  ChapterQuizRecordMapper chapterQuizRecordMapper,
                                  CourseChapterMapper courseChapterMapper,
                                  CourseStudentMapper courseStudentMapper,
                                  LearningRecordMapper learningRecordMapper) {
        this.chapterQuizMapper = chapterQuizMapper;
        this.chapterQuizRecordMapper = chapterQuizRecordMapper;
        this.courseChapterMapper = courseChapterMapper;
        this.courseStudentMapper = courseStudentMapper;
        this.learningRecordMapper = learningRecordMapper;
    }

    @Override
    public List<ChapterQuiz> listByChapter(Long chapterId, Long userId, Integer role) {
        CourseChapter chapter = requireChapter(chapterId);
        if (role != null && role == RoleConstant.STUDENT) {
            ensureJoined(chapter.getCourseId(), userId);
        }
        LambdaQueryWrapper<ChapterQuiz> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChapterQuiz::getChapterId, chapterId)
                .orderByAsc(ChapterQuiz::getSeq)
                .orderByAsc(ChapterQuiz::getId);
        List<ChapterQuiz> quizzes = chapterQuizMapper.selectList(wrapper);
        if (role != null && role == RoleConstant.STUDENT) {
            quizzes.forEach(item -> item.setAnswer(null));
        }
        return quizzes;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChapterQuiz save(ChapterQuizSaveRequest request) {
        if (request.getChapterId() == null) {
            throw new BusinessException(400, "请选择章节");
        }
        CourseChapter chapter = requireChapter(request.getChapterId());
        ChapterQuiz quiz = request.getId() == null ? new ChapterQuiz() : chapterQuizMapper.selectById(request.getId());
        if (quiz == null) {
            throw new BusinessException(404, "小测题目不存在");
        }
        quiz.setCourseId(request.getCourseId() == null ? chapter.getCourseId() : request.getCourseId());
        quiz.setChapterId(request.getChapterId());
        quiz.setType(request.getType() == null ? 1 : request.getType());
        quiz.setContent(request.getContent());
        quiz.setOptions(toJson(request.getOptions()));
        quiz.setAnswer(request.getAnswer());
        quiz.setAnalysis(request.getAnalysis());
        quiz.setScore(request.getScore() == null ? 5 : request.getScore());
        quiz.setSeq(request.getSeq() == null ? nextSeq(request.getChapterId()) : request.getSeq());
        quiz.setUpdateTime(LocalDateTime.now());
        if (quiz.getId() == null) {
            quiz.setCreateTime(LocalDateTime.now());
            chapterQuizMapper.insert(quiz);
        } else {
            chapterQuizMapper.updateById(quiz);
        }
        return quiz;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        int rows = chapterQuizMapper.deleteById(id);
        if (rows == 0) {
            throw new BusinessException(404, "小测题目不存在");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<ChapterQuizResult> submit(Long studentId, ChapterQuizSubmitRequest request) {
        CourseChapter chapter = requireChapter(request.getChapterId());
        ensureJoined(chapter.getCourseId(), studentId);
        LambdaQueryWrapper<ChapterQuiz> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChapterQuiz::getChapterId, request.getChapterId());
        Map<Long, ChapterQuiz> quizMap = chapterQuizMapper.selectList(wrapper).stream()
                .collect(Collectors.toMap(ChapterQuiz::getId, Function.identity(), (a, b) -> a));
        Map<Long, ChapterQuizRecord> existing = chapterQuizRecordMapper
                .selectByChapterAndStudent(request.getChapterId(), studentId).stream()
                .collect(Collectors.toMap(ChapterQuizRecord::getQuizId, Function.identity(), (a, b) -> a));

        List<ChapterQuizResult> results = new ArrayList<>();
        int totalScore = 0;
        for (ChapterQuizSubmitRequest.AnswerItem item : request.getAnswers()) {
            ChapterQuiz quiz = quizMap.get(item.getQuizId());
            if (quiz == null) {
                continue;
            }
            boolean correct = isCorrect(quiz, item.getAnswer());
            int score = correct ? (quiz.getScore() == null ? 0 : quiz.getScore()) : 0;
            totalScore += score;
            String feedback = correct ? "回答正确。" : ("回答错误。" + (quiz.getAnalysis() == null ? "" : quiz.getAnalysis()));

            ChapterQuizRecord record = existing.get(quiz.getId());
            boolean isNew = record == null;
            if (isNew) {
                record = new ChapterQuizRecord();
                record.setCourseId(chapter.getCourseId());
                record.setChapterId(chapter.getId());
                record.setQuizId(quiz.getId());
                record.setStudentId(studentId);
            }
            record.setAnswer(item.getAnswer());
            record.setScore(score);
            record.setIsCorrect(correct ? 1 : 0);
            record.setFeedback(feedback);
            record.setCreateTime(LocalDateTime.now());
            if (isNew) {
                chapterQuizRecordMapper.insert(record);
            } else {
                chapterQuizRecordMapper.updateById(record);
            }

            ChapterQuizResult result = new ChapterQuizResult();
            result.setQuizId(quiz.getId());
            result.setAnswer(item.getAnswer());
            result.setScore(score);
            result.setCorrect(correct);
            result.setFeedback(feedback);
            results.add(result);
        }
        syncLearningRecord(studentId, chapter, totalScore);
        return results;
    }

    /** 校验章节是否存在 */
    private CourseChapter requireChapter(Long chapterId) {
        CourseChapter chapter = courseChapterMapper.selectById(chapterId);
        if (chapter == null) {
            throw new BusinessException(404, "章节不存在");
        }
        return chapter;
    }

    /** 校验学生已加入课程 */
    private void ensureJoined(Long courseId, Long studentId) {
        if (studentId == null || courseStudentMapper.countByCourseAndStudent(courseId, studentId) == 0) {
            throw new BusinessException(403, "请先加入课程再参与章节学习");
        }
    }

    /** 查询下一个题目序号 */
    private int nextSeq(Long chapterId) {
        LambdaQueryWrapper<ChapterQuiz> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChapterQuiz::getChapterId, chapterId);
        return chapterQuizMapper.selectList(wrapper).stream()
                .map(ChapterQuiz::getSeq)
                .filter(seq -> seq != null)
                .max(Integer::compareTo)
                .orElse(0) + 1;
    }

    /** 客观题即时判分 */
    private boolean isCorrect(ChapterQuiz quiz, String answer) {
        String std = quiz.getAnswer() == null ? "" : quiz.getAnswer().trim();
        String stu = answer == null ? "" : answer.trim();
        if (stu.isEmpty()) {
            return false;
        }
        if (quiz.getType() != null && quiz.getType() == 2) {
            return toCharSet(std).equals(toCharSet(stu));
        }
        return std.equalsIgnoreCase(stu);
    }

    /** 多选答案拆成字符集合 */
    private Set<String> toCharSet(String text) {
        Set<String> set = new HashSet<>();
        if (text == null) {
            return set;
        }
        for (char c : text.toCharArray()) {
            if (Character.isLetterOrDigit(c)) {
                set.add(String.valueOf(Character.toUpperCase(c)));
            }
        }
        return set;
    }

    /** 序列化选项 */
    private String toJson(List<String> options) {
        if (options == null) {
            return "[]";
        }
        try {
            return objectMapper.writeValueAsString(options);
        } catch (Exception e) {
            return "[]";
        }
    }

    /** 小测成绩写入学习行为，后续学情分析可直接使用 */
    private void syncLearningRecord(Long studentId, CourseChapter chapter, int score) {
        LearningRecord record = new LearningRecord();
        record.setStudentId(studentId);
        record.setCourseId(chapter.getCourseId());
        record.setActionType("章节小测");
        record.setTargetId(chapter.getId());
        record.setDuration((chapter.getDurationMinutes() == null ? 15 : chapter.getDurationMinutes()) * 60);
        record.setScore(score);
        record.setCreateTime(LocalDateTime.now());
        learningRecordMapper.insert(record);
    }
}
