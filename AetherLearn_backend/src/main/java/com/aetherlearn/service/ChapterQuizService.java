package com.aetherlearn.service;

import com.aetherlearn.dto.ChapterQuizSaveRequest;
import com.aetherlearn.dto.ChapterQuizSubmitRequest;
import com.aetherlearn.entity.ChapterQuiz;
import lombok.Data;

import java.util.List;

/**
 * 章节小测服务接口
 */
public interface ChapterQuizService {

    /**
     * 查询章节小测题目
     */
    List<ChapterQuiz> listByChapter(Long chapterId, Long userId, Integer role);

    /**
     * 教师新增/编辑题目
     */
    ChapterQuiz save(ChapterQuizSaveRequest request);

    /**
     * 教师删除题目
     */
    void delete(Long id);

    /**
     * 学生提交章节小测
     */
    List<ChapterQuizResult> submit(Long studentId, ChapterQuizSubmitRequest request);

    /**
     * 小测提交结果
     */
    @Data
    class ChapterQuizResult implements java.io.Serializable {
        /** 题目ID */
        private Long quizId;
        /** 学生作答 */
        private String answer;
        /** 本题得分 */
        private Integer score;
        /** 是否正确 */
        private Boolean correct;
        /** 反馈/解析 */
        private String feedback;
    }
}
