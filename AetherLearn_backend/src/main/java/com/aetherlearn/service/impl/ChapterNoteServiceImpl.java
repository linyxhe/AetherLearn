package com.aetherlearn.service.impl;

import com.aetherlearn.common.BusinessException;
import com.aetherlearn.dto.ChapterNoteSaveRequest;
import com.aetherlearn.entity.ChapterNote;
import com.aetherlearn.entity.CourseChapter;
import com.aetherlearn.mapper.ChapterNoteMapper;
import com.aetherlearn.mapper.CourseChapterMapper;
import com.aetherlearn.mapper.CourseStudentMapper;
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
    private final CourseChapterMapper courseChapterMapper;
    private final CourseStudentMapper courseStudentMapper;

    public ChapterNoteServiceImpl(ChapterNoteMapper chapterNoteMapper,
                                  CourseChapterMapper courseChapterMapper,
                                  CourseStudentMapper courseStudentMapper) {
        this.chapterNoteMapper = chapterNoteMapper;
        this.courseChapterMapper = courseChapterMapper;
        this.courseStudentMapper = courseStudentMapper;
    }

    @Override
    public ChapterNote getByChapter(Long studentId, Long chapterId) {
        CourseChapter chapter = requireJoinedChapter(studentId, chapterId);
        return chapterNoteMapper.selectByStudentAndChapter(studentId, chapterId);
    }

    @Override
    public List<ChapterNote> listMine(Long studentId, Long courseId) {
        if (courseId != null && courseStudentMapper.countByCourseAndStudent(courseId, studentId) == 0) {
            throw new BusinessException(403, "请先加入该课程再查看笔记");
        }
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
        CourseChapter chapter = requireJoinedChapter(studentId, request.getChapterId());
        if (request.getCourseId() != null && !java.util.Objects.equals(request.getCourseId(), chapter.getCourseId())) {
            throw new BusinessException(400, "笔记课程与章节所属课程不一致");
        }
        ChapterNote note = chapterNoteMapper.selectByStudentAndChapter(studentId, request.getChapterId());
        if (note == null) {
            note = new ChapterNote();
            note.setStudentId(studentId);
            // 课程 ID 由服务端根据章节派生，不能信任客户端传入值。
            note.setCourseId(chapter.getCourseId());
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

    /** 查询章节并校验学生已加入对应课程，避免跨课程写入或读取笔记。 */
    private CourseChapter requireJoinedChapter(Long studentId, Long chapterId) {
        CourseChapter chapter = courseChapterMapper.selectById(chapterId);
        if (chapter == null) {
            throw new BusinessException(404, "章节不存在");
        }
        if (studentId == null || courseStudentMapper.countByCourseAndStudent(chapter.getCourseId(), studentId) == 0) {
            throw new BusinessException(403, "请先加入该课程再操作笔记");
        }
        return chapter;
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
