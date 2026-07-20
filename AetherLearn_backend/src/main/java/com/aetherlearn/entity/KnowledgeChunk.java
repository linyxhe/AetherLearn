package com.aetherlearn.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 知识切片实体（F-KB / RAG 检索语料）
 * <p>对应表 {@code knowledge_chunk}；v2.0 含 {@code embedding}（预留向量值，当前可 NULL）。</p>
 */
@Data
@TableName("knowledge_chunk")
public class KnowledgeChunk implements Serializable {

    /** 切片ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属文档ID */
    private Long docId;

    /** 所属课程ID（检索按课程隔离） */
    private Long courseId;

    /** 切片序号 */
    private Integer seq;

    /** 切片文本内容 */
    private String content;

    /** 文本长度 */
    private Integer charLen;

    /** 向量值（预留，当前可 NULL） */
    private String embedding;

    /** 创建时间 */
    private LocalDateTime createTime;
}
