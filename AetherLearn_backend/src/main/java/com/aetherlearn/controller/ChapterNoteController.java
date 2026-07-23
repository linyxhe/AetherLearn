package com.aetherlearn.controller;

import com.aetherlearn.common.Result;
import com.aetherlearn.common.SecurityUtils;
import com.aetherlearn.dto.ChapterNoteSaveRequest;
import com.aetherlearn.entity.ChapterNote;
import com.aetherlearn.service.ChapterNoteService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 章节笔记控制器（F-NOTE）
 */
@RestController
@RequestMapping("/api/chapter-notes")
public class ChapterNoteController {

    private final ChapterNoteService chapterNoteService;

    public ChapterNoteController(ChapterNoteService chapterNoteService) {
        this.chapterNoteService = chapterNoteService;
    }

    /** 查询指定章节的我的笔记 */
    @PreAuthorize("hasRole('STUDENT')")
    @GetMapping("/chapter/{chapterId}")
    public Result<ChapterNote> getByChapter(@PathVariable Long chapterId) {
        return Result.success(chapterNoteService.getByChapter(SecurityUtils.getCurrentUserId(), chapterId));
    }

    /** 查询我的笔记列表 */
    @PreAuthorize("hasRole('STUDENT')")
    @GetMapping
    public Result<List<ChapterNote>> list(@RequestParam(required = false) Long courseId) {
        return Result.success(chapterNoteService.listMine(SecurityUtils.getCurrentUserId(), courseId));
    }

    /** 保存章节笔记 */
    @PreAuthorize("hasRole('STUDENT')")
    @PostMapping
    public Result<ChapterNote> save(@Valid @RequestBody ChapterNoteSaveRequest request) {
        return Result.success("笔记已保存", chapterNoteService.save(SecurityUtils.getCurrentUserId(), request));
    }

    /** 删除章节笔记 */
    @PreAuthorize("hasRole('STUDENT')")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        chapterNoteService.delete(SecurityUtils.getCurrentUserId(), id);
        return Result.success();
    }
}
