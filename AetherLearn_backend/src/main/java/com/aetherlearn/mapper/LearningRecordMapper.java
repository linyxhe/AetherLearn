package com.aetherlearn.mapper;

import com.aetherlearn.entity.LearningRecord;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 学习行为记录 Mapper（F-LEARN 学情模块）
 * <p>继承 {@code BaseMapper} 获得基础 CRUD；提供按学生+作业查询"作业"行为记录（批改后回写成绩）。</p>
 */
@Mapper
public interface LearningRecordMapper extends BaseMapper<LearningRecord> {

    /**
     * 查询某学生某作业关联的"作业"行为记录（用于批改/复核后回写总分）
     */
    @Select("SELECT * FROM learning_record " +
            "WHERE student_id = #{studentId} AND action_type = '作业' AND target_id = #{assignmentId} " +
            "ORDER BY id DESC LIMIT 1")
    LearningRecord selectHomeworkRecord(@Param("studentId") Long studentId,
                                        @Param("assignmentId") Long assignmentId);

    /** 查询学生某章节小测的唯一学习记录，提交重试时执行更新而不是重复插入。 */
    @Select("SELECT * FROM learning_record WHERE student_id = #{studentId} "
            + "AND action_type = '章节小测' AND target_id = #{chapterId} "
            + "ORDER BY id DESC LIMIT 1")
    LearningRecord selectQuizRecord(@Param("studentId") Long studentId,
                                    @Param("chapterId") Long chapterId);

    /**
     * 查询学生是否已经完成指定在线学习章节。
     */
    @Select("SELECT COUNT(1) FROM learning_record " +
            "WHERE student_id = #{studentId} AND action_type = '章节学习' AND target_id = #{chapterId}")
    int countChapterRecord(@Param("studentId") Long studentId, @Param("chapterId") Long chapterId);
}
