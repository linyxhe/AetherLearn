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
    /** 检索总耗时（混合检索时为 Embedding + Milvus，本地降级时为 BM25） */
    private long retrievalMs;
    /** Embedding 阶段耗时（本地降级时为 0） */
    private long embeddingMs;
    /** Milvus 召回阶段耗时（本地降级时为 0） */
    private long milvusMs;
    /** 重排序阶段耗时（未启用或降级时为 0） */
    private long rerankMs;
    /** LLM 调用耗时 */
    private long llmMs;
    /** 数据库写入耗时 */
    private long dbMs;

    /** 实际检索策略：hybrid / bm25 */
    private String retrievalStrategy;
    /** 检索降级原因；正常混合检索时为空 */
    private String fallbackReason;
    /** Embedding 阶段状态：ok / skipped / degraded */
    private String embeddingStatus;
    /** Milvus 阶段状态：ok / skipped / degraded */
    private String milvusStatus;
    /** 重排序阶段状态：ok / skipped / degraded */
    private String rerankStatus;

    /** 本轮回答由谁生成：python（Python 编排服务） / retrieval（未使用大模型，直接返回检索结果） */
    private String llmProvider;
    /** 未使用大模型时的原因；正常由 Python 生成时为空 */
    private String llmFallbackReason;
    /** ReAct 智能助教实际执行的工具轮数；0 表示模型判断资料够用、没调用任何工具 */
    private int agentToolRounds;
}
