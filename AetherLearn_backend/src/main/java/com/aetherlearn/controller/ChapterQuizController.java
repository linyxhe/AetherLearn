package com.aetherlearn.controller;

import com.aetherlearn.common.Result;
import com.aetherlearn.common.SecurityUtils;
import com.aetherlearn.dto.ChapterQuizSaveRequest;
import com.aetherlearn.dto.ChapterQuizSubmitRequest;
import com.aetherlearn.entity.ChapterQuiz;
import com.aetherlearn.service.ChapterQuizService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 章节小测控制器（F-QUIZ / F-ERROR）
 * <p>路径：/api/chapter-quiz/**；学生在章节学习页答题，教师可维护题目。</p>
 */
@RestController
@RequestMapping("/api/chapter-quiz")
public class ChapterQuizController {

    private final ChapterQuizService chapterQuizService;

    public ChapterQuizController(ChapterQuizService chapterQuizService) {
        this.chapterQuizService = chapterQuizService;
    }

    /**
     * 查询章节小测题目；学生视角隐藏标准答案。
     */
    @GetMapping("/chapter/{chapterId}")
    public Result<List<ChapterQuiz>> listByChapter(@PathVariable Long chapterId) {
        return Result.success(chapterQuizService.listByChapter(
                chapterId,
                SecurityUtils.getCurrentUserId(),
                SecurityUtils.getCurrentRole()
        ));
    }

    /**
     * 教师新增/编辑章节小测题目。
     */
    @PreAuthorize("hasRole('TEACHER')")
    @PostMapping("/question")
    public Result<ChapterQuiz> saveQuestion(@RequestBody ChapterQuizSaveRequest request) {
        return Result.success("题目保存成功", chapterQuizService.save(request));
    }

    /**
     * 教师删除章节小测题目。
     */
    @PreAuthorize("hasRole('TEACHER')")
    @DeleteMapping("/question/{id}")
    public Result<Void> deleteQuestion(@PathVariable Long id) {
        chapterQuizService.delete(id);
        return Result.success();
    }

    /**
     * 学生提交章节小测并即时判分。
     */
    @PreAuthorize("hasRole('STUDENT')")
    @PostMapping("/submit")
    public Result<List<ChapterQuizService.ChapterQuizResult>> submit(@Valid @RequestBody ChapterQuizSubmitRequest request) {
        return Result.success("提交成功", chapterQuizService.submit(SecurityUtils.getCurrentUserId(), request));
    }
}

