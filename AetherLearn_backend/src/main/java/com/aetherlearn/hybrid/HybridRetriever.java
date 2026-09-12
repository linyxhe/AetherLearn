package com.aetherlearn.hybrid;

import com.aetherlearn.ai.PythonAiClient;
import com.aetherlearn.entity.KnowledgeChunk;
import com.aetherlearn.kb.Bm25Retriever;
import com.aetherlearn.mapper.KnowledgeChunkMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 混合检索编排器。
 * <p>优先调用 Python AI 服务的 Milvus 混合检索；当 Python 服务不可用、返回空结果或
 * 结果无法映射为 MySQL 切片时，自动回退到 Java 侧 MySQL FULLTEXT + BM25，保证知识库问答不中断。</p>
 */
@Slf4j
@Component
public class HybridRetriever {

    private final PythonAiClient pythonAiClient;
    private final Bm25Retriever bm25Retriever;
    private final KnowledgeChunkMapper chunkMapper;
    private final boolean pythonEnabled;
    private final int pythonTopK;
    private final int rerankTopK;

    public HybridRetriever(PythonAiClient pythonAiClient,
                           Bm25Retriever bm25Retriever,
                           KnowledgeChunkMapper chunkMapper,
                           @Value("${ai.python-service.enabled:true}") boolean pythonEnabled,
                           @Value("${ai.python-service.top-k:30}") int pythonTopK,
                           @Value("${ai.python-service.rerank-top-k:10}") int rerankTopK) {
        this.pythonAiClient = pythonAiClient;
        this.bm25Retriever = bm25Retriever;
        this.chunkMapper = chunkMapper;
        this.pythonEnabled = pythonEnabled;
        this.pythonTopK = pythonTopK;
        this.rerankTopK = rerankTopK;
    }

    /** Python 侧重排序保留数；问答链路把它透传给 Python，避免两处各配一份默认值。 */
    public int getRerankTopK() {
        return rerankTopK;
    }

    /**
     * 检索课程知识库切片。
     *
     * @param courseId 课程ID
     * @param query    查询文本
     * @param topK     最终返回数量
     * @return 按相关性降序排列的切片
     */
    public List<KnowledgeChunk> retrieve(Long courseId, String query, int topK) {
        return retrieveWithMetrics(courseId, query, topK).getChunks();
    }

    /**
     * 检索并返回分阶段耗时、检索策略和降级原因，供问答响应展示检索链路指标。
     *
     * @param courseId 课程ID
     * @param query    查询文本
     * @param topK     最终返回数量
     * @return 检索结果及指标
     */
    public RetrievalResult retrieveWithMetrics(Long courseId, String query, int topK) {
        // 默认带精排：非 Agent 链路（以及 Agent 关闭时）的行为与引入开关前完全一致。
        return retrieveWithMetrics(courseId, query, topK, true);
    }

    /**
     * 检索并返回分阶段耗时、检索策略和降级原因，可指定是否启用精排。
     *
     * @param useReranker 是否启用 Cross-Encoder 精排。CPU 上 12 条候选约 10.8s，
     *                    是单问最大的耗时项。Agent 模式下的首轮检索**跳过精排**：
     *                    模型本来就会自己判断资料够不够、必要时用工具再检索一次，
     *                    首轮抢先付 10.8s 并不划算；精度交由模型按需索取。
     */
    public RetrievalResult retrieveWithMetrics(Long courseId, String query, int topK, boolean useReranker) {
        // 参数边界校验：避免负数或超大 Top-K 传入 Python 或 BM25。
        if (courseId == null || query == null || query.isBlank() || topK <= 0) {
            return fallbackResult(
                    new ArrayList<>(),
                    0,
                    "invalid_parameter",
                    null
            );
        }

        PythonAiClient.RetrievalResult pythonResult = null;
        if (pythonEnabled) {
            try {
                pythonResult = pythonAiClient.hybridSearch(
                        courseId, query, pythonTopK, rerankTopK, useReranker
                );
                if (pythonResult != null && !pythonResult.getHits().isEmpty()) {
                    List<KnowledgeChunk> chunks = mapHitsToChunks(pythonResult.getHits());
                    if (!chunks.isEmpty()) {
                        List<KnowledgeChunk> limited = limit(chunks, topK);
                        log.info("[Hybrid] Python 混合检索命中 {} 条，最终返回 {} 条，策略={}，精排={}",
                                chunks.size(), limited.size(), pythonResult.getStrategy(),
                                useReranker ? "开" : "跳过");
                        // Python 的 retrieval_time_ms 只算 Embedding + Milvus（便于区分各阶段成本），
                        // 但"检索总耗时"必须把精排算进去，否则前端看到的检索耗时会比实际少十几秒。
                        long retrievalTotalMs = pythonResult.getEmbeddingMs()
                                + pythonResult.getMilvusMs()
                                + pythonResult.getRerankMs();
                        if (retrievalTotalMs <= 0) {
                            retrievalTotalMs = pythonResult.getRetrievalMs() + pythonResult.getRerankMs();
                        }
                        return new RetrievalResult(
                                limited,
                                retrievalTotalMs,
                                pythonResult.getRerankMs(),
                                true,
                                pythonResult.getEmbeddingMs(),
                                pythonResult.getMilvusMs(),
                                pythonResult.getStrategy() != null ? pythonResult.getStrategy() : "hybrid",
                                pythonResult.getFallbackReason(),
                                pythonResult.getEmbeddingStatus(),
                                pythonResult.getMilvusStatus(),
                                pythonResult.getRerankStatus()
                        );
                    }
                    log.warn("[Hybrid] Python 命中 {} 条但无法映射为 MySQL 切片，回退到 MySQL FULLTEXT + BM25",
                            pythonResult.getHits().size());
                    return fallbackToBm25(courseId, query, topK, "python_unmapped", pythonResult);
                }
                if (pythonResult == null) {
                    // 使用客户端记录的细分原因（密钥 401、超时、连接失败等），避免统一误报为“服务不可用”。
                    String reason = pythonAiClient.getLastFailureReason();
                    if (reason == null || reason.isBlank()) {
                        reason = "python_unavailable";
                    }
                    log.warn("[Hybrid] Python 混合检索未返回结果（{}），回退到 MySQL FULLTEXT + BM25", reason);
                    return fallbackToBm25(courseId, query, topK, reason, null);
                }
                log.warn("[Hybrid] Python 混合检索未返回可用切片，回退到 MySQL FULLTEXT + BM25");
                return fallbackToBm25(courseId, query, topK, "python_empty", pythonResult);
            } catch (Exception e) {
                log.warn("[Hybrid] Python 混合检索失败，回退到 MySQL FULLTEXT + BM25：{}", e.getMessage(), e);
                return fallbackToBm25(courseId, query, topK, "python_error", pythonResult);
            }
        }

        log.info("[Hybrid] Python AI 服务未启用，直接使用 MySQL FULLTEXT + BM25");
        return fallbackToBm25(courseId, query, topK, "python_disabled", null);
    }

    /** 回退到 Java 本地 BM25，并保留 Python 侧已产生的阶段指标用于排查。 */
    private RetrievalResult fallbackToBm25(Long courseId,
                                           String query,
                                           int topK,
                                           String fallbackReason,
                                           PythonAiClient.RetrievalResult pythonResult) {
        long started = System.currentTimeMillis();
        List<KnowledgeChunk> chunks = bm25Retriever.retrieve(courseId, query, topK);
        long elapsedMs = System.currentTimeMillis() - started;
        return fallbackResult(chunks, elapsedMs, fallbackReason, pythonResult);
    }

    /** 组装本地降级结果，尽量保留 Python 阶段状态便于前端定位。 */
    private RetrievalResult fallbackResult(List<KnowledgeChunk> chunks,
                                           long elapsedMs,
                                           String fallbackReason,
                                           PythonAiClient.RetrievalResult pythonResult) {
        long embeddingMs = pythonResult != null ? pythonResult.getEmbeddingMs() : 0;
        long milvusMs = pythonResult != null ? pythonResult.getMilvusMs() : 0;
        String embeddingStatus = pythonResult != null && pythonResult.getEmbeddingStatus() != null
                ? pythonResult.getEmbeddingStatus() : "skipped";
        String milvusStatus = pythonResult != null && pythonResult.getMilvusStatus() != null
                ? pythonResult.getMilvusStatus() : "skipped";
        return new RetrievalResult(
                chunks,
                elapsedMs,
                0,
                false,
                embeddingMs,
                milvusMs,
                "bm25",
                fallbackReason,
                embeddingStatus,
                milvusStatus,
                "skipped"
        );
    }

    /** 将 Python 返回的 Milvus hit 映射回 MySQL 知识切片实体。 */
    private List<KnowledgeChunk> mapHitsToChunks(List<PythonAiClient.RetrievalHit> hits) {
        List<KnowledgeChunk> chunks = new ArrayList<>();
        for (PythonAiClient.RetrievalHit hit : hits) {
            KnowledgeChunk chunk = null;
            if (hit.getChunkId() != null) {
                chunk = chunkMapper.selectById(hit.getChunkId());
            }
            // 旧 Milvus 集合没有 chunk_id 时，使用文档 ID 和序号回退定位。
            if (chunk == null && hit.getDocId() != null && hit.getSeq() != null) {
                chunk = chunkMapper.selectByDocIdAndSeq(hit.getCourseId(), hit.getDocId(), hit.getSeq());
            }
            if (chunk != null) {
                chunks.add(chunk);
            }
        }
        return chunks;
    }

    /** 限制最终返回数量，避免上下文拼接超过预期。 */
    private List<KnowledgeChunk> limit(List<KnowledgeChunk> chunks, int topK) {
        int size = Math.max(1, topK);
        return chunks.size() <= size ? chunks : new ArrayList<>(chunks.subList(0, size));
    }

    /** 混合检索结果及分阶段耗时指标。 */
    public static class RetrievalResult {
        private final List<KnowledgeChunk> chunks;
        private final long retrievalMs;
        private final long rerankMs;
        private final boolean usedPython;
        private final long embeddingMs;
        private final long milvusMs;
        private final String strategy;
        private final String fallbackReason;
        private final String embeddingStatus;
        private final String milvusStatus;
        private final String rerankStatus;

        /** 兼容旧调用。 */
        public RetrievalResult(List<KnowledgeChunk> chunks, long retrievalMs, long rerankMs, boolean usedPython) {
            this(chunks, retrievalMs, rerankMs, usedPython, 0, 0,
                    usedPython ? "hybrid" : "bm25", null, "ok", "ok", "skipped");
        }

        public RetrievalResult(List<KnowledgeChunk> chunks,
                               long retrievalMs,
                               long rerankMs,
                               boolean usedPython,
                               long embeddingMs,
                               long milvusMs,
                               String strategy,
                               String fallbackReason,
                               String embeddingStatus,
                               String milvusStatus,
                               String rerankStatus) {
            this.chunks = chunks;
            this.retrievalMs = retrievalMs;
            this.rerankMs = rerankMs;
            this.usedPython = usedPython;
            this.embeddingMs = embeddingMs;
            this.milvusMs = milvusMs;
            this.strategy = strategy;
            this.fallbackReason = fallbackReason;
            this.embeddingStatus = embeddingStatus;
            this.milvusStatus = milvusStatus;
            this.rerankStatus = rerankStatus;
        }

        public List<KnowledgeChunk> getChunks() {
            return chunks;
        }

        public long getRetrievalMs() {
            return retrievalMs;
        }

        public long getRerankMs() {
            return rerankMs;
        }

        public boolean isUsedPython() {
            return usedPython;
        }

        public long getEmbeddingMs() {
            return embeddingMs;
        }

        public long getMilvusMs() {
            return milvusMs;
        }

        public String getStrategy() {
            return strategy;
        }

        public String getFallbackReason() {
            return fallbackReason;
        }

        public String getEmbeddingStatus() {
            return embeddingStatus;
        }

        public String getMilvusStatus() {
            return milvusStatus;
        }

        public String getRerankStatus() {
            return rerankStatus;
        }
    }
}
