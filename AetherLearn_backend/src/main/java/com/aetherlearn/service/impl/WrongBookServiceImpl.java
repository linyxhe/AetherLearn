package com.aetherlearn.service.impl;

import com.aetherlearn.dto.WrongBookItemVO;
import com.aetherlearn.entity.Assignment;
import com.aetherlearn.entity.ChapterQuiz;
import com.aetherlearn.entity.ChapterQuizRecord;
import com.aetherlearn.entity.Course;
import com.aetherlearn.entity.CourseChapter;
import com.aetherlearn.entity.Question;
import com.aetherlearn.entity.StudentAnswer;
import com.aetherlearn.mapper.AssignmentMapper;
import com.aetherlearn.mapper.ChapterQuizMapper;
import com.aetherlearn.mapper.ChapterQuizRecordMapper;
import com.aetherlearn.mapper.CourseChapterMapper;
import com.aetherlearn.mapper.CourseMapper;
import com.aetherlearn.mapper.QuestionMapper;
import com.aetherlearn.mapper.StudentAnswerMapper;
import com.aetherlearn.service.WrongBookService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * 错题本服务实现
 */
@Service
public class WrongBookServiceImpl implements WrongBookService {

    private final StudentAnswerMapper studentAnswerMapper;
    private final QuestionMapper questionMapper;
    private final AssignmentMapper assignmentMapper;
    private final CourseMapper courseMapper;
    private final ChapterQuizRecordMapper chapterQuizRecordMapper;
    private final ChapterQuizMapper chapterQuizMapper;
    private final CourseChapterMapper courseChapterMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public WrongBookServiceImpl(StudentAnswerMapper studentAnswerMapper,
                                QuestionMapper questionMapper,
                                AssignmentMapper assignmentMapper,
                                CourseMapper courseMapper,
                                ChapterQuizRecordMapper chapterQuizRecordMapper,
                                ChapterQuizMapper chapterQuizMapper,
                                CourseChapterMapper courseChapterMapper) {
        this.studentAnswerMapper = studentAnswerMapper;
        this.questionMapper = questionMapper;
        this.assignmentMapper = assignmentMapper;
        this.courseMapper = courseMapper;
        this.chapterQuizRecordMapper = chapterQuizRecordMapper;
        this.chapterQuizMapper = chapterQuizMapper;
        this.courseChapterMapper = courseChapterMapper;
    }

    @Override
    public List<WrongBookItemVO> list(Long studentId, String sourceType, Long courseId) {
        List<WrongBookItemVO> result = new ArrayList<>();
        boolean includeAssignment = sourceType == null || sourceType.isBlank() || "ASSIGNMENT".equalsIgnoreCase(sourceType);
        boolean includeChapter = sourceType == null || sourceType.isBlank() || "CHAPTER_QUIZ".equalsIgnoreCase(sourceType);

        if (includeAssignment) {
            result.addAll(listAssignmentWrong(studentId, courseId));
        }
        if (includeChapter) {
            result.addAll(listChapterWrong(studentId, courseId));
        }
        result.sort(Comparator.comparing(WrongBookItemVO::getCreateTime, Comparator.nullsLast(Comparator.reverseOrder())));
        return result;
    }

    private List<WrongBookItemVO> listAssignmentWrong(Long studentId, Long courseId) {
        List<WrongBookItemVO> result = new ArrayList<>();
        List<StudentAnswer> wrongAnswers = studentAnswerMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<StudentAnswer>()
                        .eq(StudentAnswer::getStudentId, studentId)
                        .eq(StudentAnswer::getIsCorrect, 0)
        );
        for (StudentAnswer sa : wrongAnswers) {
            Question q = questionMapper.selectById(sa.getQuestionId());
            if (q == null) continue;
            Assignment assignment = assignmentMapper.selectById(sa.getAssignmentId());
            if (assignment == null) continue;
            if (courseId != null && !Objects.equals(courseId, assignment.getCourseId())) continue;
            Course course = courseMapper.selectById(assignment.getCourseId());
            result.add(buildAssignmentItem(sa, q, assignment, course));
        }
        return result;
    }

    private List<WrongBookItemVO> listChapterWrong(Long studentId, Long courseId) {
        List<WrongBookItemVO> result = new ArrayList<>();
        List<ChapterQuizRecord> wrongRecords = chapterQuizRecordMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ChapterQuizRecord>()
                        .eq(ChapterQuizRecord::getStudentId, studentId)
                        .eq(ChapterQuizRecord::getIsCorrect, 0)
        );
        for (ChapterQuizRecord record : wrongRecords) {
            ChapterQuiz quiz = chapterQuizMapper.selectById(record.getQuizId());
            if (quiz == null) continue;
            if (courseId != null && !Objects.equals(courseId, quiz.getCourseId())) continue;
            Course course = courseMapper.selectById(quiz.getCourseId());
            CourseChapter chapter = courseChapterMapper.selectById(quiz.getChapterId());
            result.add(buildChapterItem(record, quiz, course, chapter));
        }
        return result;
    }

    private WrongBookItemVO buildAssignmentItem(StudentAnswer sa, Question q, Assignment assignment, Course course) {
        WrongBookItemVO vo = new WrongBookItemVO();
        vo.setSourceType("ASSIGNMENT");
        vo.setCourseId(course != null ? course.getId() : assignment.getCourseId());
        vo.setCourseName(course != null ? course.getCourseName() : "未知课程");
        vo.setSourceTitle(assignment.getTitle());
        vo.setQuestionId(q.getId());
        vo.setType(q.getType());
        vo.setContent(q.getContent());
        vo.setOptions(parseOptions(q.getOptions()));
        vo.setStandardAnswer(q.getAnswer());
        vo.setYourAnswer(sa.getAnswer());
        vo.setAnalysis(sa.getFeedback() == null || sa.getFeedback().isBlank() ? q.getAnalysis() : sa.getFeedback());
        vo.setScore(sa.getScore());
        vo.setCreateTime(sa.getCreateTime());
        return vo;
    }

    private WrongBookItemVO buildChapterItem(ChapterQuizRecord record, ChapterQuiz quiz, Course course, CourseChapter chapter) {
        WrongBookItemVO vo = new WrongBookItemVO();
        vo.setSourceType("CHAPTER_QUIZ");
        vo.setCourseId(course != null ? course.getId() : quiz.getCourseId());
        vo.setCourseName(course != null ? course.getCourseName() : "未知课程");
        vo.setSourceTitle(chapter != null ? chapter.getTitle() : "章节小测");
        vo.setChapterId(quiz.getChapterId());
        vo.setChapterTitle(chapter != null ? chapter.getTitle() : "未知章节");
        vo.setQuestionId(quiz.getId());
        vo.setType(quiz.getType());
        vo.setContent(quiz.getContent());
        vo.setOptions(parseOptions(quiz.getOptions()));
        vo.setStandardAnswer(quiz.getAnswer());
        vo.setYourAnswer(record.getAnswer());
        vo.setAnalysis(record.getFeedback() != null ? record.getFeedback() : quiz.getAnalysis());
        vo.setScore(record.getScore());
        vo.setCreateTime(record.getCreateTime());
        return vo;
    }

    @SuppressWarnings("unchecked")
    private List<String> parseOptions(String text) {
        if (text == null || text.isBlank()) return List.of();
        try {
            return objectMapper.readValue(text, List.class);
        } catch (Exception e) {
            return List.of();
        }
    }
}
