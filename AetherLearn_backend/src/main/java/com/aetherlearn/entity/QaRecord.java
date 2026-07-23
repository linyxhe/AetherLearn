package com.aetherlearn.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 智能问答记录实体（F-QA 智能答疑模块）
 * <p>对应表 {@code qa_record}。</p>
 */
@Data
@TableName("qa_record")
public class QaRecord implements Serializable {

    /** 记录ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 提问用户ID */
    private Long userId;

    /** 所属课程ID */
    private Long courseId;

    /** 用户提问 */
    private String question;

    /** 系统回答 */
    private String answer;

    /** 引用的切片ID列表（如 "1,2,3"） */
    private String sourceChunks;

    /** 来源切片详情 JSON（M4 问答历史来源显示） */
    private String sourcesJson;

    /** 是否使用大模型：1-是 0-否 */
    private Integer useLlm;

    /** 回答耗时（毫秒） */
    private Integer costMs;

    /** 提问时间 */
    private LocalDateTime createTime;
}
