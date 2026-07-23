package com.aetherlearn.service;

import com.aetherlearn.dto.ChapterNoteSaveRequest;
import com.aetherlearn.entity.ChapterNote;

import java.util.List;

/**
 * 章节笔记服务（F-NOTE）
 */
public interface ChapterNoteService {

    /** 查询指定章节笔记 */
    ChapterNote getByChapter(Long studentId, Long chapterId);

    /** 查询我的全部笔记 */
    List<ChapterNote> listMine(Long studentId, Long courseId);

    /** 保存章节笔记 */
    ChapterNote save(Long studentId, ChapterNoteSaveRequest request);

    /** 删除章节笔记 */
    void delete(Long studentId, Long id);
}
