package com.aetherlearn.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 课程知识库文档实体（F-KB 知识库模块）
 * <p>对应表 {@code knowledge_doc}；v2.0 含 {@code is_deleted} 软删除。</p>
 */
@Data
@TableName("knowledge_doc")
public class KnowledgeDoc implements Serializable {

    /** 文档ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属课程ID */
    private Long courseId;

    /** 文档标题 */
    private String title;

    /** 原文件存储路径（uploads/knowledge） */
    private String filePath;

    /** 文件类型：pdf/docx/md/txt */
    private String fileType;

    /** 文件大小（字节） */
    private Long fileSize;

    /** 切片数量 */
    private Integer chunkCount;

    /** 状态：1-已解析 0-解析中 2-失败 */
    private Integer status;

    /** 软删除标志：0-未删 1-已删 */
    @TableLogic
    private Integer isDeleted;

    /** 上传人ID */
    private Long uploadBy;

    /** 上传时间 */
    private LocalDateTime createTime;
}
