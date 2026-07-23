package com.aetherlearn.dto;

import lombok.Data;

import java.util.List;

/**
 * 智能问答响应（F-QA 智能答疑模块）
 */
@Data
public class QaAnswer {

    /** 回答内容 */
    private String answer;

    /** 是否使用大模型（true-大模型生成 / false-仅知识库检索降级） */
    private boolean useLlm;

    /** 回答耗时（毫秒） */
    private long costMs;

    /** 引用的知识来源片段 */
    private List<QaSource> sources;

    // L8 分阶段耗时统计（毫秒）
    /** BM25 检索耗时 */
    private long retrievalMs;
    /** LLM 调用耗时 */
    private long llmMs;
    /** 数据库写入耗时 */
    private long dbMs;
}
