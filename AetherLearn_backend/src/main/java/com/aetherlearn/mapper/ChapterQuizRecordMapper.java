package com.aetherlearn.mapper;

import com.aetherlearn.entity.ChapterQuizRecord;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 章节小测作答记录 Mapper
 */
@Mapper
public interface ChapterQuizRecordMapper extends BaseMapper<ChapterQuizRecord> {

    /**
     * 查询某学生某章节的作答记录
     */
    @Select("SELECT * FROM chapter_quiz_record WHERE chapter_id = #{chapterId} AND student_id = #{studentId}")
    List<ChapterQuizRecord> selectByChapterAndStudent(@Param("chapterId") Long chapterId,
                                                      @Param("studentId") Long studentId);
}
