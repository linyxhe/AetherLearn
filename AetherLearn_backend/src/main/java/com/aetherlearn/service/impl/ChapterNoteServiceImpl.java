package com.aetherlearn.service.impl;

import com.aetherlearn.common.BusinessException;
import com.aetherlearn.dto.ChapterNoteSaveRequest;
import com.aetherlearn.entity.ChapterNote;
import com.aetherlearn.mapper.ChapterNoteMapper;
import com.aetherlearn.service.ChapterNoteService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 章节笔记服务实现（F-NOTE）
 */
@Service
public class ChapterNoteServiceImpl implements ChapterNoteService {

    private final ChapterNoteMapper chapterNoteMapper;

    public ChapterNoteServiceImpl(ChapterNoteMapper chapterNoteMapper) {
        this.chapterNoteMapper = chapterNoteMapper;
    }

    @Override
    public ChapterNote getByChapter(Long studentId, Long chapterId) {
        return chapterNoteMapper.selectByStudentAndChapter(studentId, chapterId);
    }

    @Override
    public List<ChapterNote> listMine(Long studentId, Long courseId) {
        LambdaQueryWrapper<ChapterNote> wrapper = new LambdaQueryWrapper<ChapterNote>()
                .eq(ChapterNote::getStudentId, studentId)
                .orderByDesc(ChapterNote::getUpdateTime);
        if (courseId != null) {
            wrapper.eq(ChapterNote::getCourseId, courseId);
        }
        return chapterNoteMapper.selectList(wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChapterNote save(Long studentId, ChapterNoteSaveRequest request) {
        ChapterNote note = chapterNoteMapper.selectByStudentAndChapter(studentId, request.getChapterId());
        if (note == null) {
            note = new ChapterNote();
            note.setStudentId(studentId);
            note.setCourseId(request.getCourseId());
            note.setChapterId(request.getChapterId());
            note.setCreateTime(LocalDateTime.now());
        }
        note.setTitle((request.getTitle() == null || request.getTitle().isBlank()) ? "章节笔记" : request.getTitle());
        note.setContent(request.getContent());
        note.setFavorite(request.getFavorite() == null ? 0 : request.getFavorite());
        note.setUpdateTime(LocalDateTime.now());
        if (note.getId() == null) {
            chapterNoteMapper.insert(note);
        } else {
            chapterNoteMapper.updateById(note);
        }
        return note;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long studentId, Long id) {
        ChapterNote note = chapterNoteMapper.selectById(id);
        if (note == null) {
            return;
        }
        if (!studentId.equals(note.getStudentId())) {
            throw new BusinessException(403, "只能删除自己的笔记");
        }
        chapterNoteMapper.deleteById(id);
    }
}
