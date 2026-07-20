package com.aetherlearn.dto;

import lombok.Data;

/**
 * 问答引用的知识来源（F-QA 智能答疑模块）
 * <p>用于前端在回答下方展示“依据了哪些资料片段”。</p>
 */
@Data
public class QaSource {

    /** 来源文档ID */
    private Long docId;

    /** 来源文档标题 */
    private String docTitle;

    /** 切片文本内容 */
    private String content;
}
