package com.aetherlearn.mapper;

import com.aetherlearn.entity.ChapterNote;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 章节笔记 Mapper（F-NOTE）
 */
@Mapper
public interface ChapterNoteMapper extends BaseMapper<ChapterNote> {

    /** 查询学生在指定章节的笔记 */
    @Select("SELECT * FROM chapter_note WHERE student_id = #{studentId} AND chapter_id = #{chapterId} LIMIT 1")
    ChapterNote selectByStudentAndChapter(@Param("studentId") Long studentId, @Param("chapterId") Long chapterId);
}
