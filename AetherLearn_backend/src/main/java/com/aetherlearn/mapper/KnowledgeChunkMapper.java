package com.aetherlearn.mapper;

import com.aetherlearn.entity.KnowledgeChunk;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 知识切片 Mapper（F-KB / RAG 检索语料）
 * <p>继承 {@code BaseMapper} 获得基础 CRUD；提供按课程批量取切片用于 BM25 检索。</p>
 */
@Mapper
public interface KnowledgeChunkMapper extends BaseMapper<KnowledgeChunk> {

    /**
     * 查询某课程的全部切片（用于 BM25 检索语料）
     * <p>自定义 SQL 需手动过滤 {@code is_deleted=0}（逻辑删除仅在 MP 自动生成的 SQL 中生效）。</p>
     */
    @Select("SELECT c.* FROM knowledge_chunk c " +
            "JOIN knowledge_doc d ON c.doc_id = d.id " +
            "WHERE c.course_id = #{courseId} AND c.is_deleted = 0 AND d.is_deleted = 0")
    List<KnowledgeChunk> selectByCourseId(Long courseId);
}
